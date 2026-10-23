"""Independent emulator walking controls, starting from live GPS; no UI or recording actions."""
import argparse
import concurrent.futures
import math
import os
import re
import subprocess
import sys
import time
import uuid

from sartak_demo import Demo, HERE, RUNS, STATE as DEMO_STATE, read_json, save_json

STATE=RUNS/'walk-state.json'
QUEUE=RUNS/'walk-commands'
REPLIES=RUNS/'walk-replies'


def destination(latitude,longitude,bearing,meters):
    angle=meters/6371008.8
    lat,lon,course=map(math.radians,(latitude,longitude,bearing))
    new_lat=math.asin(math.sin(lat)*math.cos(angle)+math.cos(lat)*math.sin(angle)*math.cos(course))
    new_lon=lon+math.atan2(math.sin(course)*math.sin(angle)*math.cos(lat),
                         math.cos(angle)-math.sin(lat)*math.sin(new_lat))
    return math.degrees(new_lat),(math.degrees(new_lon)+180)%360-180


def parse_location(dump):
    # Prefer the provider's latest fix, not an old location in a request/history.
    for provider in ('gps','fused'):
        match=re.search(r'last location=Location\['+provider+
                        r'\s+([-+\d.]+),\s*([-+\d.]+)([^\]\n]*)',dump)
        if match:
            lat,lon=float(match[1]),float(match[2])
            if not (-90<=lat<=90 and -180<=lon<=180):
                continue
            altitude=re.search(r'\balt=([-+\d.]+)',match[3])
            if altitude is None:
                raise RuntimeError('The current GPS fix has no altitude; send a full fix in Emulator Location first.')
            return dict(latitude=lat,longitude=lon,altitude=float(altitude[1]))
    raise RuntimeError('No live GPS fix found. Set a location in Emulator Extended Controls > Location first.')


class Walker:
    def __init__(self,demo):
        self.demo=demo
        self.people={}

    def handle(self,request):
        action=request['command']
        device=request.get('device','both')
        selected=list(self.demo.devices) if device=='both' else [('hq' if device=='lead' else device)]
        if action=='shutdown':
            selected=list(self.people)
        if action in ('start','resume'):
            # Capture every selected device before changing any state.
            locations={}
            for name in selected:
                serial=self.demo.devices[name]
                if not serial.startswith('emulator-'):
                    raise RuntimeError('Walking is restricted to emulator serials.')
                locations[name]=parse_location(self.demo.adb_call(serial,'shell','dumpsys','location'))
            for name in selected:
                previous=self.people.get(name,{})
                self.people[name]=dict(**locations[name],serial=self.demo.devices[name],moving=True,
                    speedMps=request.get('speed') or previous.get('speedMps',1.2),
                    bearing=request['bearing'] if request.get('bearing') is not None else previous.get('bearing',0),
                    metersWalked=previous.get('metersWalked',0),error=None,_last=time.monotonic())
            return
        if action=='stop':
            # A device never started here is not given a synthetic GPS fix.
            for name in selected:
                if name in self.people:
                    self.people[name]['moving']=False
            return
        if action=='shutdown':
            for person in self.people.values():
                person['moving']=False
            return
        for name in selected:
            if name not in self.people:
                raise RuntimeError(f'{name} is not controlled yet. Use start -Device {name} first.')
        for name in selected:
            person=self.people[name]
            if action=='speed':
                if request.get('speed') is None:
                    raise RuntimeError('speed requires -Speed, in metres per second.')
                person['speedMps']=request['speed']
            elif action=='faster':
                person['speedMps']=min(10,round(person['speedMps']+request['step'],4))
            elif action=='slower':
                person['speedMps']=max(.1,round(person['speedMps']-request['step'],4))
            elif action=='direction':
                if request.get('bearing') is None:
                    raise RuntimeError('direction requires -Bearing (0 north, 90 east, 180 south, 270 west).')
                person['bearing']=request['bearing']
            else:
                raise ValueError(f'Unknown command: {action}')

    def tick(self,now):
        for person in self.people.values():
            elapsed=max(0,min(3,now-person['_last']))
            person['_last']=now
            if person['moving']:
                meters=elapsed*person['speedMps']
                person['latitude'],person['longitude']=destination(
                    person['latitude'],person['longitude'],person['bearing'],meters)
                person['metersWalked']+=meters

    def inject(self,name):
        person=self.people[name]
        try:
            self.demo.fix(person['serial'],person['latitude'],person['longitude'],
                          person['speedMps'] if person['moving'] else 0,
                          person['bearing'],altitude=person['altitude'])
            person['error']=None
        except Exception as exc:
            person['moving']=False
            person['error']=str(exc)

    def public(self):
        return {name:{key:value for key,value in person.items() if not key.startswith('_')}
                for name,person in self.people.items()}


def responsive(state):
    return bool(state and state.get('running') and time.time()-state.get('heartbeat',0)<10)


def release_demo(demo):
    old=read_json(DEMO_STATE,{})
    if responsive(old):
        if old.get('recording'):
            raise RuntimeError('The earlier demo recorder is active. Finish it with demo.ps1 record-stop first.')
        if old.get('devices')!=demo.devices:
            raise RuntimeError('The earlier demo controls different devices. Stop it explicitly first.')
        print('Releasing the earlier demo GPS feed before reading current positions...',flush=True)
        demo.send('stop')


def ensure_worker(demo):
    state=read_json(STATE,{})
    if responsive(state):
        if state.get('devices')!=demo.devices:
            raise RuntimeError('Walking worker controls different devices. Run shutdown first.')
        return
    release_demo(demo)
    QUEUE.mkdir(parents=True,exist_ok=True)
    REPLIES.mkdir(parents=True,exist_ok=True)
    with (RUNS/'walk-worker.log').open('a',encoding='utf-8') as log:
        subprocess.Popen([sys.executable,str(HERE/'walk.py'),'worker','--config',str(demo.config_path)],
                         stdout=log,stderr=log,
                         creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    deadline=time.monotonic()+10
    while time.monotonic()<deadline:
        if responsive(read_json(STATE,{})):
            return
        time.sleep(.1)
    raise RuntimeError('Walking worker did not start. See runs/walk-worker.log.')


def worker(demo):
    import msvcrt
    lock=(RUNS/'walk.lock').open('a+b')
    try:
        lock.seek(0)
        if not lock.read(1):
            lock.write(b'0'); lock.flush()
        lock.seek(0)
        msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
    except OSError:
        lock.close()
        return
    controller=Walker(demo)
    running=True
    pool=concurrent.futures.ThreadPoolExecutor(max_workers=2)
    try:
        while running:
            tick=time.monotonic()
            controller.tick(tick)
            receipts=[]
            for file in sorted(QUEUE.glob('*.json')):
                request=read_json(file)
                file.unlink()
                if time.time()-request['createdAt']>30:
                    receipts.append((request['id'],'Expired command; resend it.'))
                    continue
                try:
                    controller.handle(request)
                    if request['command']=='shutdown':
                        running=False
                    receipts.append((request['id'],None))
                except Exception as exc:
                    receipts.append((request['id'],str(exc)))
            # Refuse competing GPS writers if someone restarts the old demo.
            if responsive(read_json(DEMO_STATE,{})):
                for person in controller.people.values():
                    person['moving']=False
                    person['error']='The earlier demo GPS worker was restarted. Stop it before using walk.'
                running=False
            else:
                futures=[pool.submit(controller.inject,name) for name in controller.people]
                for future in futures:
                    future.result()
            state=dict(pid=os.getpid(),heartbeat=time.time(),running=running,
                       devices=demo.devices,people=controller.public())
            save_json(STATE,state)
            for identifier,error in receipts:
                save_json(REPLIES/(identifier+'.json'),dict(error=error,state=state))
            if running:
                time.sleep(max(.05,1-(time.monotonic()-tick)))
    finally:
        pool.shutdown(wait=True)
        lock.close()


def describe(state):
    print('Walking controller: '+('active' if responsive(state) else 'not running'))
    for name,person in state.get('people',{}).items():
        label='Lead' if name=='hq' else 'Member'
        print(f'{label} ({person["serial"]}): {"walking" if person["moving"] else "stopped"}; '
              f'{person["speedMps"]:.2f} m/s ({person["speedMps"]*3.6:.1f} km/h); '
              f'heading {person["bearing"]:.0f} degrees; '
              f'{person["latitude"]:.7f}, {person["longitude"]:.7f}')
        if person.get('error'):
            print('  ERROR: '+person['error'])


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=('start','resume','stop','speed','faster','slower',
                                         'direction','status','shutdown','worker'))
    parser.add_argument('--device',choices=('lead','hq','member','both'),default='both')
    parser.add_argument('--speed',type=float)
    parser.add_argument('--bearing',type=float)
    parser.add_argument('--step',type=float,default=.2)
    parser.add_argument('--config',default=str(HERE/'demo.json'))
    args=parser.parse_args()
    try:
        if args.speed is not None and not .1<=args.speed<=10:
            raise ValueError('Speed must be 0.1-10 m/s. Use stop to halt.')
        if args.bearing is not None and not 0<=args.bearing<360:
            raise ValueError('Bearing must be 0-359.999 degrees.')
        if not .1<=args.step<=5:
            raise ValueError('Step must be 0.1-5 m/s.')
        if args.command=='speed' and args.speed is None:
            raise ValueError('speed requires -Speed.')
        if args.command=='direction' and args.bearing is None:
            raise ValueError('direction requires -Bearing.')
        demo=Demo(args.config)
        if args.command=='worker':
            worker(demo)
        elif args.command=='status':
            describe(read_json(STATE,{}))
            for name,serial in demo.devices.items():
                position=parse_location(demo.adb_call(serial,'shell','dumpsys','location'))
                print(f'Live {name}: {serial} {position["latitude"]:.7f}, {position["longitude"]:.7f}')
        else:
            state=read_json(STATE,{})
            if args.command not in ('start','resume') and not responsive(state):
                print('Walking has not started. No device was changed.')
                return 0
            ensure_worker(demo)
            identifier=uuid.uuid4().hex
            request=vars(args).copy()
            request.update(id=identifier,createdAt=time.time())
            save_json(QUEUE/f'{time.time_ns()}-{identifier}.json',request)
            replyfile=REPLIES/(identifier+'.json')
            deadline=time.monotonic()+25
            while time.monotonic()<deadline:
                reply=read_json(replyfile)
                if reply:
                    replyfile.unlink()
                    if reply['error']:
                        raise RuntimeError(reply['error'])
                    describe(reply['state'])
                    return 0
                time.sleep(.1)
            raise RuntimeError('Command timed out. Check runs/walk-worker.log and run status.')
    except KeyboardInterrupt:
        print('Command interrupted. Use walk.ps1 stop to halt movement.')
        return 1
    except Exception as exc:
        print('ERROR: '+str(exc),file=sys.stderr)
        return 1
    return 0


if __name__=='__main__':
    sys.exit(main())
