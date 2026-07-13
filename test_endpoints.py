import urllib.request
import urllib.parse
import json

def api_fetch(url, cookie=None, method='GET', body=None):
    req = urllib.request.Request(url, method=method)
    if cookie: req.add_header('Cookie', cookie)
    if body:
        req.add_header('Content-Type', 'application/json')
        req.data = json.dumps(body).encode('utf-8')
    try:
        resp = urllib.request.urlopen(req)
        return resp.status, json.loads(resp.read().decode('utf-8')), resp.headers.get('Set-Cookie')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8'), None

# Login
status, data, set_cookie = api_fetch('http://localhost:8080/api/auth/login', method='POST', body={'email': 'admin@newsahara.com', 'password': 'password'})
print("LOGIN:", status)

cookie = ""
if set_cookie:
    cookie = set_cookie.split(';')[0]

status, data, _ = api_fetch('http://localhost:8080/api/projects', cookie=cookie)
print("PROJECTS:", status, len(data.get('content', [])) if isinstance(data, dict) else data)
if isinstance(data, dict) and data.get('content'):
    print("Project 1 siteStore:", data['content'][0].get('siteStore'))

status, data, _ = api_fetch('http://localhost:8080/api/items', cookie=cookie)
print("ITEMS:", status, len(data.get('content', [])) if isinstance(data, dict) else data)

status, data, _ = api_fetch('http://localhost:8080/api/audit-log', cookie=cookie)
print("AUDIT LOG:", status, len(data.get('content', [])) if isinstance(data, dict) else data)

