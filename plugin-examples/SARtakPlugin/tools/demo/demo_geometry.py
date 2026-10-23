"""WGS84 route geometry for simulated GPS, independent of SARtak application data."""
import math


def utm(latitude, longitude):
    zone = int((longitude + 180) // 6) + 1
    lat, lon = math.radians(latitude), math.radians(longitude)
    origin = math.radians((zone - 1) * 6 - 177)
    a, e2, k = 6378137.0, 0.0066943799901413165, 0.9996
    ep2 = e2 / (1 - e2)
    n = a / math.sqrt(1 - e2 * math.sin(lat)**2)
    t, c = math.tan(lat)**2, ep2 * math.cos(lat)**2
    aa = math.cos(lat) * (lon - origin)
    m = a * ((1-e2/4-3*e2**2/64-5*e2**3/256)*lat
             -(3*e2/8+3*e2**2/32+45*e2**3/1024)*math.sin(2*lat)
             +(15*e2**2/256+45*e2**3/1024)*math.sin(4*lat)
             -(35*e2**3/3072)*math.sin(6*lat))
    east = k*n*(aa+(1-t+c)*aa**3/6
                +(5-18*t+t*t+72*c-58*ep2)*aa**5/120)+500000
    north = k*(m+n*math.tan(lat)*(aa*aa/2
                +(5-t+9*c+4*c*c)*aa**4/24
                +(61-58*t+t*t+600*c-330*ep2)*aa**6/720))
    return zone, east, north + (10000000 if latitude < 0 else 0)


def geographic(east, north, latitude, longitude):
    # Invert the same local projection with Newton iteration.
    for _ in range(8):
        _, e, n = utm(latitude, longitude)
        _, el, nl = utm(latitude + 0.00001, longitude)
        _, eo, no = utm(latitude, longitude + 0.00001)
        a, b, c, d = (el-e)/0.00001, (eo-e)/0.00001, (nl-n)/0.00001, (no-n)/0.00001
        det = a*d-b*c
        latitude += ((east-e)*d-b*(north-n))/det
        longitude += (a*(north-n)-(east-e)*c)/det
    return latitude, longitude


def make_plan(config):
    lat, lon = config['latitude'], config['longitude']
    zone, east, north = utm(lat, lon)
    west, south = math.floor(east/100)*100, math.floor(north/100)*100
    candidates = []
    for angle in range(360):
        de = config['memberSeparationMeters'] * math.cos(math.radians(angle))
        dn = config['memberSeparationMeters'] * math.sin(math.radians(angle))
        margin = min(east+de-west, west+100-east-de, north+dn-south, south+100-north-dn)
        if margin >= 2:
            candidates.append((abs(dn), -margin, de, dn))
    if not candidates:
        raise ValueError('No member offset fits in the starting 100 m cell.')
    _, _, de, dn = min(candidates)
    mlat, mlon = geographic(east+de, north+dn, lat, lon)
    points = [(east, north)]
    rows, columns = config['routeRows'], config['routeColumns']
    top = south + (rows-.25)*100
    bottom = max(south+25, south+2-dn)
    for column in range(columns):
        x, y = east+column*100, top if column % 2 == 0 else bottom
        points.append((x, y))
        if column < columns-1:
            for step in range(1,13):
                theta = math.pi*step/12
                points.append((x+50-50*math.cos(theta),
                               y+(1 if column % 2 == 0 else -1)*20*math.sin(theta)))
    route, distance = [], 0.0
    for i, (x,y) in enumerate(points):
        if i:
            distance += math.dist(points[i-1], (x,y))
        plat, plon = geographic(x,y,lat,lon)
        route.append(dict(easting=x,northing=y,latitude=plat,longitude=plon,distance=distance))
    band = 'CDEFGHJKLMNPQRSTUVWX'[min(19,int((lat+80)//8))]
    corners=[]
    for x,y in [(west+50,south+50),(west+(columns-.5)*100,south+(rows-.5)*100)]:
        clat,clon=geographic(x,y,lat,lon)
        corners.append(dict(easting=x,northing=y,latitude=clat,longitude=clon))
    return dict(zone=f'{zone}{band}',cellWest=west,cellSouth=south,
                leaderStart=dict(latitude=lat,longitude=lon,easting=east,northing=north),
                memberStart=dict(latitude=mlat,longitude=mlon,easting=east+de,northing=north+dn),
                memberOffset=dict(east=de,north=dn),route=route,lengthMeters=distance,
                selectionCorners=corners)


def route_point(plan, distance):
    points=plan['route']
    distance=min(max(0,distance),points[-1]['distance'])
    for a,b in zip(points,points[1:]):
        if distance<=b['distance']:
            fraction=(distance-a['distance'])/max(.0001,b['distance']-a['distance'])
            return (a['easting']+(b['easting']-a['easting'])*fraction,
                    a['northing']+(b['northing']-a['northing'])*fraction)
    return points[-1]['easting'],points[-1]['northing']
