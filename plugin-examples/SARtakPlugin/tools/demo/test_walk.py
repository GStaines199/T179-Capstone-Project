"""Control/geometry regression tests; no ADB calls or emulator changes."""
import math
import unittest

from walk import Walker, destination, parse_location
from sartak_demo import Demo


class FakeDemo:
    devices={'hq':'emulator-5556','member':'emulator-5554'}

    def __init__(self):
        self.fixes=[]

    def adb_call(self,serial,*arguments):
        assert arguments==('shell','dumpsys','location')
        point='-26.360123,150.512345' if serial=='emulator-5556' else '-26.360678,150.512987'
        return f'last location=Location[gps {point} hAcc=5 alt=412.3 vel=0.0 bear=0.0]'

    def fix(self,*arguments,**keywords):
        self.fixes.append((arguments,keywords))


class WalkTests(unittest.TestCase):
    def setUp(self):
        self.demo=FakeDemo()
        self.walker=Walker(self.demo)

    def command(self,name,device='both',**extras):
        self.walker.handle(dict(command=name,device=device,speed=None,bearing=None,step=.2,**extras))

    def test_start_captures_current_positions_without_using_demo_origin(self):
        self.command('start')
        self.assertEqual(self.walker.people['hq']['latitude'],-26.360123)
        self.assertEqual(self.walker.people['member']['longitude'],150.512987)
        self.assertEqual(self.walker.people['hq']['altitude'],412.3)
        self.assertEqual(self.demo.fixes,[])

    def test_devices_move_and_stop_independently(self):
        self.command('start')
        for person in self.walker.people.values():
            person['_last']=10
        self.walker.tick(11)
        self.command('stop','member')
        lead_before=self.walker.people['hq']['latitude']
        member_before=self.walker.people['member']['latitude']
        self.walker.tick(12)
        self.assertGreater(self.walker.people['hq']['latitude'],lead_before)
        self.assertEqual(self.walker.people['member']['latitude'],member_before)
        self.walker.inject('member')
        arguments,keywords=self.demo.fixes[-1]
        self.assertEqual(arguments[3],0)
        self.assertEqual(keywords['altitude'],412.3)

    def test_speed_changes_are_independent_and_do_not_resume_stopped_device(self):
        self.command('start')
        self.command('faster','hq')
        self.assertAlmostEqual(self.walker.people['hq']['speedMps'],1.4)
        self.assertAlmostEqual(self.walker.people['member']['speedMps'],1.2)
        self.command('stop','member')
        self.command('slower','member')
        self.assertFalse(self.walker.people['member']['moving'])
        self.assertAlmostEqual(self.walker.people['member']['speedMps'],1.0)

    def test_no_fix_is_sent_to_uncontrolled_device(self):
        self.command('start','hq')
        self.walker.inject('hq')
        self.assertNotIn('member',self.walker.people)
        self.assertEqual(len(self.demo.fixes),1)
        self.assertEqual(self.demo.fixes[0][0][0],'emulator-5556')

    def test_resume_recaptures_live_position(self):
        self.command('start','hq')
        self.walker.people['hq']['latitude']=-20
        self.command('resume','hq')
        self.assertEqual(self.walker.people['hq']['latitude'],-26.360123)

    def test_geographic_movement_follows_course_at_walking_distance(self):
        lat,lon=destination(-26.3,150.4,0,1.2)
        self.assertGreater(lat,-26.3)
        self.assertAlmostEqual(lon,150.4,places=8)
        self.assertAlmostEqual(math.radians(lat+26.3)*6371008.8,1.2,places=5)
        lat,lon=destination(-26.3,150.4,90,1.2)
        self.assertGreater(lon,150.4)
        self.assertAlmostEqual(lat,-26.3,places=8)

    def test_parser_prefers_latest_gps_over_fused_and_history(self):
        point=parse_location('history Location[gps -1,2 alt=0]\n'
                             'last location=Location[fused -3,4 alt=5]\n'
                             'last location=Location[gps -6,7 alt=8]')
        self.assertEqual(point,dict(latitude=-6,longitude=7,altitude=8))
        with self.assertRaises(RuntimeError):
            parse_location('last location=null')

    def test_gps_transport_preserves_altitude_override(self):
        demo=Demo.__new__(Demo)
        demo.config={'altitudeMeters':350}
        calls=[]
        demo.adb_call=lambda *args:(calls.append(args) or 'OK')
        demo.fix('emulator-5556',-26,150,1.2,0,altitude=412.3)
        self.assertEqual(calls[0][6],412.3)


if __name__=='__main__':
    unittest.main()
