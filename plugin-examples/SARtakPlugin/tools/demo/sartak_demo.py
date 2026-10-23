"""Two-AVD demo controls. Uses real UI workflows; no Ditto credentials are read or saved."""
import argparse
import datetime as dt
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

from demo_geometry import geographic, make_plan, route_point, utm

HERE=Path(__file__).resolve().parent
RUNS=HERE/'runs'
STATE=RUNS/'state.json'
QUEUE=RUNS/'commands'
PACKAGE='com.atakmap.app.civ'
PREFIX='com.atakmap.android.plugintemplate.'


def save_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_name(path.name+'.'+uuid.uuid4().hex+'.tmp')
    temp.write_text(json.dumps(value,indent=2),encoding='utf-8')
    os.replace(temp,path)


def read_json(path,default=None):
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except FileNotFoundError:
        return default


def utc():
    return dt.datetime.now(dt.timezone.utc).isoformat(timespec='milliseconds')


class Demo:
    def __init__(self,config_path):
        self.config_path=Path(config_path).resolve()
        self.config=read_json(self.config_path)
        if not self.config:
            raise ValueError(f'Config not found: {self.config_path}')
        sdk=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
        sdk=Path(sdk) if sdk else Path(os.environ.get('LOCALAPPDATA',''))/'Android'/'Sdk'
        self.adb=str(sdk/'platform-tools'/'adb.exe')
        if not Path(self.adb).exists():
            self.adb=shutil.which('adb')
        if not self.adb:
            raise RuntimeError('adb not found. Install Android SDK Platform Tools.')
        self.devices=dict(hq=self.config['hqSerial'],member=self.config['memberSerial'])
        if self.devices['hq']==self.devices['member']:
            raise ValueError('HQ and member must use different serials.')
        self.plan=make_plan(self.config)

    def adb_call(self,serial,*arguments,timeout=20):
        result=subprocess.run([self.adb,'-s',serial,*map(str,arguments)],
                              capture_output=True,timeout=timeout)
        if result.returncode:
            raise RuntimeError(f'{serial}: adb {arguments[:3]} failed: '
                               +result.stderr.decode('utf-8',errors='replace').strip())
        return result.stdout.decode('utf-8',errors='replace').strip()

    def broadcast(self,serial,action,**extras):
        args=['shell','am','broadcast','-a',PREFIX+action,'-p',PACKAGE]
        for key,value in extras.items():
            args.extend(['--es',key,str(value)])
        return self.adb_call(serial,*args)

    def check_devices(self):
        for serial in self.devices.values():
            if not serial.startswith('emulator-'):
                raise ValueError('Simulated GPS is restricted to emulator serials.')
            if self.adb_call(serial,'get-state')!='device':
                raise RuntimeError(f'{serial} is not ready.')
            for package in (PACKAGE,'com.atakmap.android.plugintemplate.plugin'):
                if not self.adb_call(serial,'shell','pm','path',package):
                    raise RuntimeError(f'{package} missing on {serial}.')

    def ui(self,serial):
        self.adb_call(serial,'shell','uiautomator','dump','/sdcard/sartak-demo-ui.xml')
        raw=self.adb_call(serial,'shell','cat','/sdcard/sartak-demo-ui.xml')
        return ET.fromstring(raw[raw.index('<hierarchy'):])

    def fix(self,serial,latitude,longitude,speed=0,bearing=0,altitude=None):
        # geo fix takes velocity in knots, with no bearing argument.
        output=self.adb_call(serial,'emu','geo','fix',f'{longitude:.9f}',f'{latitude:.9f}',
                             self.config['altitudeMeters'] if altitude is None else altitude,
                             '10',f'{speed/.514444:.4f}')
        if 'KO:' in output:
            raise RuntimeError(f'{serial}: GPS rejected: {output}')
        # RMC supplies course, so the native marker points along the GPS path.
        now=dt.datetime.now(dt.timezone.utc)
        lat=abs(latitude); lon=abs(longitude)
        sentence=(f'GPRMC,{now:%H%M%S}.00,A,{int(lat):02d}{(lat%1)*60:07.4f},'
                  f'{"N" if latitude>=0 else "S"},{int(lon):03d}{(lon%1)*60:07.4f},'
                  f'{"E" if longitude>=0 else "W"},{speed/.514444:.3f},{bearing:.2f},'
                  f'{now:%d%m%y},,,A')
        checksum=0
        for char in sentence:
            checksum^=ord(char)
        output=self.adb_call(serial,'emu','geo','nmea',f'${sentence}*{checksum:02X}')
        if 'KO:' in output:
            raise RuntimeError(f'{serial}: NMEA rejected: {output}')

    def open_tab(self,device,tab):
        selected=self.devices.values() if device=='both' else [self.devices[device]]
        for serial in selected:
            self.broadcast(serial,'DEBUG_OPEN_TAB',tab=tab)

    def wait_for_home(self,serial,callsign=None,role=None,timeout=60):
        deadline=time.monotonic()+timeout
        last_error=None
        seen_plugin=False
        identity=''
        while time.monotonic()<deadline:
            # Broadcasts sent before ATAK loads the plugin have no receiver.
            # Repeat the harmless UI/setup actions after it becomes available.
            if role:
                self.broadcast(serial,'DEBUG_SET_ROLE',role=role)
            if callsign:
                self.broadcast(serial,'DEBUG_SET_CALLSIGN',callsign=callsign)
            self.broadcast(serial,'DEBUG_OPEN_TAB',tab='HOME')
            try:
                nodes=list(self.ui(serial).iter('node'))
            except (RuntimeError,ValueError,ET.ParseError,subprocess.TimeoutExpired) as exc:
                last_error=str(exc)
                time.sleep(1)
                continue
            plugin_nodes=[n for n in nodes if
                          'com.atakmap.android.plugintemplate.plugin:id/' in n.get('resource-id','')]
            seen_plugin=seen_plugin or bool(plugin_nodes)
            identity_node=next((n for n in plugin_nodes if
                                n.get('resource-id','').endswith('/identity_value')),None)
            identity=identity_node.get('text','').strip() if identity_node is not None else ''
            name,separator,uid=identity.rpartition(' | ')
            role_node=next((n for n in plugin_nodes if
                            n.get('resource-id','').endswith('/current_role_value')),None)
            expected_role={'HQ':'HQ','TEAM_LEAD':'Team Lead','TEAM_MEMBER':'Team Member'}.get(role)
            role_ready=not role or (role_node is not None and
                                    role_node.get('text','').strip()==expected_role)
            if separator and uid.strip() and (not callsign or name.strip()==callsign) and role_ready:
                return identity,[n.get('text','') for n in nodes]
            # Home retains its previous scroll offset when reopened. Read the
            # identity from the top rather than treating off-screen text as a
            # disabled plugin. Restrict swipes to the observed SARtak panel.
            if plugin_nodes and not identity:
                panel=next((n for n in nodes if
                            n.get('resource-id','').endswith('/right_drop_down')),None)
                bounds=re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]',
                                    panel.get('bounds','') if panel is not None else '')
                if bounds:
                    x1,y1,x2,y2=map(int,bounds.groups())
                    x=(x1+x2)//2
                    self.adb_call(serial,'shell','input','swipe',x,
                                  y1+(y2-y1)//4,x,y1+3*(y2-y1)//4,'350')
            time.sleep(1)
        diagnostic=RUNS/f'startup-{serial}.json'
        save_json(diagnostic,dict(at=utc(),serial=serial,pluginUiSeen=seen_plugin,
                                 identityVisible=bool(identity),expectedCallsign=callsign,
                                 expectedRole=role,lastUiError=last_error))
        if seen_plugin:
            reason='SARtak is loaded, but its Home identity/role did not become readable'
        else:
            reason='ATAK did not expose the SARtak Home screen; check for startup dialogs or plugin loading errors'
        raise RuntimeError(f'{reason} on {serial} within {timeout}s. Diagnostics: {diagnostic}')

    def prepare(self):
        self.check_devices()
        state=read_json(STATE,{})
        if state.get('recording') and time.time()-state.get('heartbeat',0)<6:
            raise RuntimeError('Finish the active recording before preparing another take.')
        for serial in self.devices.values():
            self.adb_call(serial,'shell','input','keyevent','KEYCODE_WAKEUP')
            self.adb_call(serial,'shell','svc','power','stayon','true')
            self.adb_call(serial,'shell','cmd','location','set-location-enabled','true')
            self.adb_call(serial,'shell','monkey','-p',PACKAGE,'-c','android.intent.category.LAUNCHER','1')
        identities=[]
        for device,serial in self.devices.items():
            print(f'Waiting for SARtak on {serial}...',flush=True)
            self.wait_for_home(serial)
            p=self.plan['leaderStart' if device=='hq' else 'memberStart']
            self.fix(serial,p['latitude'],p['longitude'])
            identity,texts=self.wait_for_home(serial,self.config[device+'Callsign'],
                                             'HQ' if device=='hq' else 'TEAM_MEMBER',timeout=30)
            identities.append(identity.rsplit(' | ',1)[1].strip())
            print(f'{device}: {serial} | {identity}')
            profile=next((t for t in texts if 'Selected Ditto profile:' in t),'Profile not visible')
            print(profile.splitlines()[0])
            if 'No active operation selected' in texts:
                print('Ready to create/join a NEW operation.')
            else:
                print('An operation is active. Use HQ Create Operation to create a new recording fixture.')
        if len(set(identities))!=2:
            raise RuntimeError('Duplicate ATAK UIDs. Resolve before a two-device demo.')
        save_json(RUNS/'plan.json',self.plan)
        self.send('reset-position')
        print('Stationary GPS feeds active; saved Ditto profiles and operations retained.')

    def ensure_worker(self):
        state=read_json(STATE,{})
        if time.time()-state.get('heartbeat',0)<8 and state.get('running'):
            if state.get('devices')!=self.devices:
                raise RuntimeError('Worker controls other devices. Stop it first.')
            if state.get('config')!=self.config:
                raise RuntimeError('Configuration changed. Run stop, then prepare to apply it.')
            return
        RUNS.mkdir(parents=True,exist_ok=True)
        QUEUE.mkdir(exist_ok=True)
        with (RUNS/'worker.log').open('a',encoding='utf-8') as log:
            subprocess.Popen([sys.executable,str(Path(__file__).resolve()),'worker',
                              '--config',str(self.config_path)],stdout=log,stderr=log,
                             creationflags=(subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0))
        deadline=time.monotonic()+12
        while time.monotonic()<deadline:
            state=read_json(STATE,{})
            if time.time()-state.get('heartbeat',0)<6 and state.get('running'):
                return
            time.sleep(.2)
        raise RuntimeError('Worker did not start. See runs/worker.log.')

    def send(self,command,**values):
        if command in ('stop','record-stop'):
            state=read_json(STATE,{})
            if not state.get('running') or time.time()-state.get('heartbeat',0)>15:
                print('No responsive demo worker. See runs/worker.log if a recorder was interrupted.')
                return state
        else:
            self.ensure_worker()
        identifier=uuid.uuid4().hex
        save_json(QUEUE/f'{time.time_ns()}-{identifier}.json',dict(id=identifier,command=command,**values))
        deadline=time.monotonic()+100
        while time.monotonic()<deadline:
            state=read_json(STATE,{})
            if state.get('lastCommand')==identifier:
                if state.get('commandError'):
                    raise RuntimeError(state['commandError'])
                return state
            time.sleep(.2)
        raise RuntimeError('Worker did not acknowledge. See runs/worker.log.')


def print_plan(demo):
    p=demo.plan
    print(f'HQ/Lead {demo.devices["hq"]}; member {demo.devices["member"]}')
    print(f'Start cell UTM {p["zone"]}: E{p["cellWest"]} N{p["cellSouth"]}')
    for label,key in [('HQ','leaderStart'),('Member','memberStart')]:
        point=p[key]
        print(f'{label}: {point["latitude"]:.9f}, {point["longitude"]:.9f}')
    print(f'{demo.config["memberSeparationMeters"]} m apart in the same 100 m UTM cell.')
    print(f'Area {demo.config["areaWidthKm"]} x {demo.config["areaHeightKm"]} km; '
          f'route {demo.config["routeColumns"]} x {demo.config["routeRows"]} cells.')
    print(f'Full route {p["lengthMeters"]:.0f} m / '
          f'{p["lengthMeters"]/demo.config["walkingSpeedMps"]/60:.1f} minutes at walking pace.')
    for index,corner in enumerate(p['selectionCorners'],1):
        print(f'Route selection corner {index}: {corner["latitude"]:.8f}, {corner["longitude"]:.8f}')


def self_test(demo):
    p=demo.plan
    for key in ('leaderStart','memberStart'):
        point=p[key]
        _,e,n=utm(point['latitude'],point['longitude'])
        assert math.floor(e/100)*100==p['cellWest']
        assert math.floor(n/100)*100==p['cellSouth']
        assert math.dist((e,n),(point['easting'],point['northing']))<.002
    assert abs(math.dist((p['leaderStart']['easting'],p['leaderStart']['northing']),
                         (p['memberStart']['easting'],p['memberStart']['northing']))
               -demo.config['memberSeparationMeters'])<.002
    for distance in (0,100,500,p['lengthMeters']):
        e,n=route_point(p,distance)
        lat,lon=geographic(e,n,demo.config['latitude'],demo.config['longitude'])
        _,e2,n2=utm(lat,lon)
        assert math.dist((e,n),(e2,n2))<.002
    assert route_point(p,10)[1]>route_point(p,0)[1]
    z,e,n=utm(0,3)
    assert z==31 and abs(e-500000)<.001 and abs(n)<.001
    print('PASS: WGS84 round trip, common 100 m starting cell, 50 m spacing, north-first route.')


def dispatch(demo,args):
    command=args.command
    if command=='worker':
        # Import once under the module name so shared paths have one definition.
        from demo_worker import run_worker
        run_worker(demo)
    elif command=='plan':
        print_plan(demo)
    elif command=='prepare':
        demo.prepare()
        print_plan(demo)
    elif command=='status':
        state=read_json(STATE,{})
        state['workerResponsive']=time.time()-state.get('heartbeat',0)<8 and state.get('running',False)
        print(json.dumps(state,indent=2))
    elif command=='open':
        demo.open_tab(args.device,args.tab)
    elif command=='lead':
        demo.broadcast(demo.devices['hq'],'DEBUG_SET_ROLE',role='TEAM_LEAD')
        demo.open_tab('hq','TEAM')
        print('HQ switched to Team Lead. Create a team through SARtak.')
    elif command=='self-test':
        self_test(demo)
    elif command=='menu':
        choices={'1':'prepare','2':'record','3':'lead','4':'move','5':'lag',
                 '6':'pause','7':'resume','8':'catch-up','9':'record-stop','0':'stop'}
        while True:
            print('\n1 Prepare | 2 Record | 3 HQ to Lead | 4 Walk | 5 Member lags')
            print('6 Pause both | 7 Resume | 8 Catch up | 9 Finish video | 0 Stop and exit')
            choice=input('Action (q closes menu while worker continues): ').strip()
            if choice.lower()=='q':
                return
            if choice not in choices:
                continue
            args.command=choices[choice]
            try:
                dispatch(demo,args)
            except Exception as exc:
                print(f'ERROR: {exc}')
            if choice=='0':
                return
    else:
        state=demo.send(command,speed=args.speed,distance=args.distance,name=args.name)
        print(f'{command}: moving={state.get("moving",False)}, member={state.get("memberMode","-")}, '
              f'recording={state.get("recording",False)}')
        if state.get('recordingDirectory'):
            print('Videos: '+state['recordingDirectory'])
        for error in state.get('gpsErrors',[])+state.get('recordingErrors',[]):
            print(f'WARNING: {error}')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',nargs='?',default='menu')
    parser.add_argument('--config',default=str(HERE/'demo.json'))
    parser.add_argument('--device',choices=('hq','member','both'),default='both')
    parser.add_argument('--tab',default='HOME')
    parser.add_argument('--speed',type=float,default=1.2)
    parser.add_argument('--distance',type=float,default=0)
    parser.add_argument('--name',default='Demo chapter')
    args=parser.parse_args()
    try:
        dispatch(Demo(args.config),args)
    except KeyboardInterrupt:
        print('\nController closed. Run demo.ps1 stop to finalize videos and stop GPS.')
    except Exception as exc:
        print(f'ERROR: {exc}',file=sys.stderr)
        return 1
    return 0


if __name__=='__main__':
    sys.exit(main())
