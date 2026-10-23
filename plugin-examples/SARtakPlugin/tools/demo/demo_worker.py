"""Background GPS/recording worker. Stops only recorder processes owned by this run."""
import concurrent.futures
import datetime as dt
import math
import os
import subprocess
import threading
import time

from demo_geometry import geographic, route_point
from sartak_demo import QUEUE, RUNS, STATE, read_json, save_json, utc


class Recording:
    def __init__(self,demo,serial,directory):
        self.demo,self.serial,self.directory=demo,serial,directory
        self.stopping=threading.Event()
        self.started=threading.Event()
        self.pidfile=None
        self.error=None
        self.clips=[]
        self.thread=threading.Thread(target=self.run,daemon=True)
        self.thread.start()

    def stop(self):
        self.stopping.set()
        self.started.wait(3)
        if self.pidfile:
            command=(f'pid=$(cat {self.pidfile} 2>/dev/null); '
                     'if [ -n "$pid" ] && grep -aq sartak-demo /proc/$pid/cmdline; '
                     'then kill -2 "$pid"; fi')
            try:
                self.demo.adb_call(self.serial,'shell',command)
            except Exception as exc:
                self.error=str(exc)

    def run(self):
        try:
            for number in range(1,1000):
                if self.stopping.is_set():
                    break
                remote=f'/sdcard/sartak-demo-{self.directory.name}-{self.serial}-{number:03}.mp4'
                self.pidfile=remote+'.pid'
                started=utc()
                command=(f'screenrecord --bit-rate {int(self.demo.config["recordBitrate"])} '
                         f'--time-limit {int(self.demo.config["recordSegmentSeconds"])} {remote} '
                         f'& pid=$!; echo $pid > {self.pidfile}; wait $pid')
                process=subprocess.Popen([self.demo.adb,'-s',self.serial,'shell',command],
                                         stdout=subprocess.PIPE,stderr=subprocess.PIPE)
                self.started.set()
                try:
                    _,errors=process.communicate(timeout=195)
                except subprocess.TimeoutExpired:
                    self.stop()
                    _,errors=process.communicate(timeout=10)
                self.pidfile=None
                local=self.directory/f'{self.serial}-{number:03}.mp4'
                self.demo.adb_call(self.serial,'pull',remote,str(local),timeout=30)
                if local.stat().st_size<1024:
                    raise RuntimeError('Recording clip was empty.')
                self.clips.append(dict(file=local.name,startedAt=started,endedAt=utc(),bytes=local.stat().st_size))
                save_json(self.directory/f'{self.serial}-clips.json',self.clips)
                if process.returncode and not self.stopping.is_set():
                    raise RuntimeError(errors.decode('utf-8',errors='replace'))
        except Exception as exc:
            self.error=str(exc)
            self.started.set()


def run_worker(demo):
    import msvcrt
    RUNS.mkdir(parents=True,exist_ok=True)
    QUEUE.mkdir(parents=True,exist_ok=True)
    lock=(RUNS/'worker.lock').open('a+b')
    try:
        lock.seek(0)
        if not lock.read(1):
            lock.write(b'0'); lock.flush()
        lock.seek(0)
        msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
    except OSError:
        lock.close()
        return
    config=demo.config
    distances=dict(hq=0.0,member=0.0)
    moving,mode,speed,stop_at=False,'follow',config['walkingSpeedMps'],0
    recordings=[]
    directory=None
    timeline=[]
    command_id=command_error=None
    last=time.monotonic()
    pool=concurrent.futures.ThreadPoolExecutor(max_workers=2)
    running=True

    def event(name,**extra):
        timeline.append(dict(at=utc(),name=name,**extra))
        if directory:
            save_json(directory/'timeline.json',timeline)

    def stop_recording():
        for recording in recordings:
            recording.stop()
        for recording in recordings:
            recording.thread.join(timeout=40)
            if recording.thread.is_alive():
                raise RuntimeError('Recorder still finalizing; check worker.log.')
        event('recording stopped',errors=[r.error for r in recordings if r.error])
        if directory:
            for serial in demo.devices.values():
                clips=[c['file'] for r in recordings if r.serial==serial for c in r.clips]
                (directory/f'{serial}-concat.txt').write_text(
                    ''.join(f"file '{name}'\n" for name in clips),encoding='utf-8')
        errors=[r.error for r in recordings if r.error]
        recordings.clear()
        if errors:
            raise RuntimeError('Recording error: '+'; '.join(errors))

    try:
        while running:
            tick=time.monotonic()
            elapsed=min(3,tick-last)
            last=tick
            # Complete any UI/recording command before the next movement tick.
            for file in sorted(QUEUE.glob('*.json')):
                request=read_json(file)
                file.unlink()
                command_id=request['id']
                command_error=None
                action=request['command']
                try:
                    if action=='move':
                        speed=float(request['speed'])
                        if not .1<=speed<=10:
                            raise ValueError('Speed must be 0.1-10 m/s.')
                        stop_at=float(request.get('distance',0))
                        moving=True
                    elif action=='pause':
                        moving=False
                    elif action=='resume':
                        if stop_at and distances['hq']>=stop_at:
                            stop_at=0
                        moving=True
                    elif action in ('lag','hold-member','catch-up'):
                        mode={'lag':'lag','hold-member':'hold','catch-up':'catch-up'}[action]
                    elif action=='reset-position':
                        moving=False
                        distances=dict(hq=0.0,member=0.0)
                        mode='follow'
                    elif action=='record':
                        if recordings:
                            raise RuntimeError('Recording already active.')
                        directory=RUNS/dt.datetime.now().strftime('%Y%m%d-%H%M%S')
                        directory.mkdir(parents=True,exist_ok=False)
                        timeline=[]
                        save_json(directory/'demo-config.json',config)
                        save_json(directory/'route-plan.json',demo.plan)
                        recordings.extend(Recording(demo,s,directory) for s in demo.devices.values())
                    elif action=='record-stop':
                        stop_recording()
                    elif action=='chapter':
                        event(request['name'])
                    elif action=='stop':
                        moving=False
                        stop_recording()
                        running=False
                    else:
                        raise ValueError(f'Unknown action: {action}')
                    event(action,distances=dict(distances))
                except Exception as exc:
                    command_error=str(exc)
                    event('command failed',action=action,error=command_error)
            if moving:
                distances['hq']+=speed*elapsed
                reached_checkpoint=bool(stop_at and distances['hq']>=stop_at)
                if reached_checkpoint:
                    distances['hq']=stop_at
                distances['hq']=min(distances['hq'],demo.plan['lengthMeters'])
                member_speed=speed*{'lag':.25,'hold':0,'catch-up':1.7}.get(mode,1)
                distances['member']=min(distances['hq'],distances['member']+member_speed*elapsed)
                if mode=='catch-up' and distances['member']>=distances['hq']-.01:
                    mode='follow'
                if reached_checkpoint:
                    moving=False
                    event('distance checkpoint reached',meters=stop_at)
                if distances['hq']>=demo.plan['lengthMeters']:
                    distances['hq']=demo.plan['lengthMeters']
                    moving=False
                    event('route finished')
            futures=[]
            positions={}
            for device,serial in demo.devices.items():
                re,rn=route_point(demo.plan,distances[device])
                e,n=re,rn
                if device=='member':
                    e+=demo.plan['memberOffset']['east']
                    n+=demo.plan['memberOffset']['north']
                lat,lon=geographic(e,n,config['latitude'],config['longitude'])
                e2,n2=route_point(demo.plan,distances[device]+1)
                bearing=math.degrees(math.atan2(e2-re,n2-rn))%360
                pace=(speed if device=='hq' else speed*{'lag':.25,'hold':0,'catch-up':1.7}.get(mode,1)) if moving else 0
                positions[device]=dict(latitude=lat,longitude=lon,speedMps=pace)
                futures.append(pool.submit(demo.fix,serial,lat,lon,pace,bearing))
            gps_errors=[]
            for future in futures:
                try:
                    future.result()
                except Exception as exc:
                    gps_errors.append(str(exc))
            save_json(STATE,dict(pid=os.getpid(),heartbeat=time.time(),devices=demo.devices,
                      config=config,
                      moving=moving,memberMode=mode,speedMps=speed,distances=distances,positions=positions,
                      recording=bool(recordings),recordingDirectory=str(directory) if directory else None,
                      recordingErrors=[r.error for r in recordings if r.error],gpsErrors=gps_errors,
                      lastCommand=command_id,commandError=command_error,running=running))
            if running:
                time.sleep(max(.05,config['gpsIntervalSeconds']-(time.monotonic()-tick)))
    finally:
        for recording in recordings:
            recording.stop()
        pool.shutdown(wait=True)
        lock.close()
