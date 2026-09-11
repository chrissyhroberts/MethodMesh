import json,os,time,binascii,hashlib
from machine import unique_id
from sensor_drivers.aht20 import ap as radio_auth,fragment,Reassembler
try: import bluetooth
except ImportError: import ubluetooth as bluetooth
try:
 import network,espnow
except ImportError:
 network=None;espnow=None
FW="methodmesh-espmesh-0.4.0";CFG="methodmesh_mesh_config.json";CFG_TMP=CFG+".tmp";CFG_BAK=CFG+".bak";SPOOL="methodmesh_mesh_spool.jsonl";TMP=SPOOL+".tmp";BAK=SPOOL+".bak"
MAX_SPOOL=96;MAX_BRIDGE=65535;MAX_WIRE=32768;GATT_BUF=512;BLE_PACKET=460;BLE_CHUNK=220;MAX_PARTIAL=8
NODE="espmesh-"+"".join("%02x"%b for b in unique_id()[-4:]);TOKEN="".join("%02x"%b for b in unique_id())
SERVICE=bluetooth.UUID("b6f2a910-9b8f-4f4e-9a1f-4f37a0010000");UP=bluetooth.UUID("b6f2a911-9b8f-4f4e-9a1f-4f37a0010000");DOWN=bluetooth.UUID("b6f2a912-9b8f-4f4e-9a1f-4f37a0010000")
C_CONNECT=1;C_DISCONNECT=2;G_WRITE=3
def compact(v): return json.dumps(v).replace(": ",":").replace(", ",",")
def sh(b):return hashlib.sha256(b).digest()
def hr(k,m):
 k=k.encode() if isinstance(k,str) else k
 if len(k)>64:k=sh(k)
 k=k+b"\0"*(64-len(k));return sh(bytes(x^92 for x in k)+sh(bytes(x^54 for x in k)+m))
def lw(p,n,k,t=2):
 if not 0<len(p)<=220:return None
 t=max(1,min(int(t),4));h=b"MR"+bytes((1,t))+sh(n.encode())[:4]+bytes((len(p),))+p;return h+hr(k,h)[:12]
def lu(r,n,k):
 if len(r)<22 or r[:2]!=b"MR" or r[2]!=1 or r[4:8]!=sh(n.encode())[:4]:return None
 z=r[8]
 if z<1 or z>220 or len(r)!=21+z:return None
 h=r[:9+z];return (r[3],bytes(r[9:9+z])) if r[9+z:]==hr(k,h)[:12] else None
def li(p):return binascii.hexlify(sh(p)[:8]).decode()
def disk_sync():
 try:
  f=getattr(os,"sync",None)
  if f:f()
 except Exception:pass
def replace_file(tmp,path,bak):
 disk_sync()
 try:os.remove(bak)
 except Exception:pass
 moved=False
 try:os.rename(path,bak);moved=True
 except Exception:pass
 try:os.rename(tmp,path)
 except Exception:
  if moved:
   try:os.rename(bak,path)
   except Exception:pass
  raise
 try:os.remove(bak)
 except Exception:pass
 disk_sync()
def config_load():
 c={"node_id":NODE,"node_name":"MethodMesh Mesh Node","provisioned":False,"network_id":"","network_key":"","provisioning_token":TOKEN,"peers":[],"phone_id":""}
 for path in (CFG,CFG_BAK):
  try:
   with open(path) as f:c.update(json.loads(f.read()))
   break
  except Exception:pass
 c["node_id"]=str(c.get("node_id") or NODE)[:64];c["node_name"]=str(c.get("node_name") or "MethodMesh Mesh Node")[:26];c["network_id"]=str(c.get("network_id") or "")[:64];c["network_key"]=str(c.get("network_key") or "")[:128];c["provisioning_token"]=str(c.get("provisioning_token") or TOKEN)[:128];c["peers"]=list(c.get("peers") or [])[:32];c["phone_id"]=str(c.get("phone_id") or "")[:128];c["provisioned"]=bool(c.get("provisioned"));return c
def config_save(c):
 with open(CFG_TMP,"w") as f:f.write(compact(c))
 replace_file(CFG_TMP,CFG,CFG_BAK)
def spool_load():
 err=""
 for path in (SPOOL,BAK):
  a=[]
  try:
   with open(path) as f:
    for l in f:
     if l.strip():a.append(json.loads(l))
   return a,""
  except OSError:pass
  except Exception as e:err=str(e) or "spool_parse_error"
 return [],err
def spool_save(a):
 with open(TMP,"w") as f:
  for r in a:f.write(compact(r)+"\n")
 replace_file(TMP,SPOOL,BAK)
def adv(name=None,services=None):
 p=bytearray()
 def add(t,v):p.extend((len(v)+1,t));p.extend(v)
 add(1,b"\x06")
 if name:add(9,name.encode()[:26])
 for s in services or ():add(7,bytes(s))
 return p
def wire_ok(w):
 try:return int(w.get("v",0))==1 and len(compact(w).encode())<=MAX_WIRE and bool(w.get("id")) and bool(w.get("kid")) and bool(w.get("op")) and bool(w.get("n")) and bool(w.get("ct"))
 except Exception:return False
class Node:
 def __init__(s):
  s.c=config_load();s.sp,s.sp_error=spool_load();s.conn=set();s.part={};s.push={};s.fr=Reassembler();s.last_radio=0;s.listen=False;s.lv=[];s.voice_until=0
  print("MethodMesh node:",s.c["node_id"]);print("Provisioning token:",s.c["provisioning_token"])
  s.ble=bluetooth.BLE();s.ble.active(True);s.ble.irq(s.irq)
  ((s.up,s.down),)=s.ble.gatts_register_services(((SERVICE,((UP,bluetooth.FLAG_WRITE),(DOWN,bluetooth.FLAG_READ|bluetooth.FLAG_NOTIFY))),))
  s.ble.gatts_set_buffer(s.up,GATT_BUF);s.ble.gatts_set_buffer(s.down,GATT_BUF);s.radio=None;s.radio_start();s.advertise()
 def advertise(s):s.ble.gap_advertise(250000,adv_data=adv(services=[SERVICE]),resp_data=adv(name=s.c["node_name"]))
 def radio_start(s):
  if network is None or espnow is None:return
  try:
   if s.radio:
    try:s.radio.active(False)
    except Exception:pass
   w=network.WLAN(network.STA_IF);w.active(True);s.radio=espnow.ESPNow();s.radio.active(True)
   for p in s.c["peers"] or ["ff:ff:ff:ff:ff:ff"]:
    try:s.radio.add_peer(bytes.fromhex(p.replace(":","")))
    except Exception:pass
  except Exception:s.radio=None
 def irq(s,e,d):
  if e==C_CONNECT:s.conn.add(d[0]);s.sync_push()
  elif e==C_DISCONNECT:s.conn.discard(d[0]);s.advertise()
  elif e==G_WRITE and d[1]==s.up:s.ble_packet(s.ble.gatts_read(s.up))
 def ble_packet(s,raw):
  try:
   q=json.loads(raw.decode())
   if q.get("protocol")!="methodmesh.blefrag":return s.bridge(q)
   if int(q.get("version",0))!=1:return
   i=str(q.get("id",""));seq=int(q.get("seq",-1));total=int(q.get("total",0))
   if not i or total<1 or total>2048 or seq<0 or seq>=total:return
   if i not in s.part:
    if len(s.part)>=MAX_PARTIAL:s.part={}
    s.part[i]={"t":total,"p":{}}
   v=s.part[i]
   if v["t"]!=total:return
   ch=binascii.a2b_base64(q.get("data",""));old=v["p"].get(seq,b"")
   if sum(len(x) for x in v["p"].values())-len(old)+len(ch)>MAX_BRIDGE:
    del s.part[i];return
   v["p"][seq]=ch
   if len(v["p"])==total:
    b=b"".join(v["p"][x] for x in range(total));del s.part[i]
    if len(b)<=MAX_BRIDGE:s.bridge(json.loads(b.decode()))
  except Exception:pass
 def notify_raw(s,b):
  if len(b)<=BLE_PACKET:return s.notify_packet(b)
  i="%08x"%(time.ticks_ms()&0xffffffff);chunks=[b[x:x+BLE_CHUNK] for x in range(0,len(b),BLE_CHUNK)]
  for n,d in enumerate(chunks):
   q={"protocol":"methodmesh.blefrag","version":1,"id":i,"seq":n,"total":len(chunks),"data":binascii.b2a_base64(d).strip().decode()};s.notify_packet(compact(q).encode())
 def notify_packet(s,b):
  if len(b)>BLE_PACKET:return
  s.ble.gatts_write(s.down,b)
  for c in tuple(s.conn):
   try:s.ble.gatts_notify(c,s.down,b)
   except Exception:pass
 def notify(s,k,r="",wire=None,body=None):
  q={"protocol":"methodmesh.gateway","version":2,"kind":k,"request_id":r,"body":body or {}}
  if wire is not None:q["wire"]=wire
  s.notify_raw(compact(q).encode())
 def bridge(s,q):
  try:
   if q.get("protocol")!="methodmesh.gateway" or int(q.get("version",0))!=2:return
   k=str(q.get("kind",""));rid=str(q.get("request_id",""));body=q.get("body") or {}
   if k=="HELLO":
    pid=str(body.get("phone_id") or "")[:128];bound=s.c.get("phone_id","")
    if pid and not bound:s.c["phone_id"]=pid;config_save(s.c)
    s.notify("HELLO_ACK",rid,body=s.info())
   elif k=="CONFIG":s.configure(body,rid)
   elif k=="DATA" and wire_ok(q.get("wire") or {}):s.from_phone(q["wire"])
   elif k=="PHONE_STORED":s.drop(str(body.get("message_id","")),"p")
   elif k=="VOICE_LISTEN":s.listen=bool(body.get("enabled"));s.notify("VOICE_LISTEN_ACK",rid,body={"enabled":s.listen})
   elif k=="LIVE_VOICE":
    p=binascii.a2b_base64(str(body.get("packet") or ""));s.voice_phone(p,int(body.get("ttl",2)))
   elif k=="SYNC_REQUEST":s.notify("SYNC_ACK",rid,body=s.info());s.sync_push()
  except Exception:pass
 def info(s):
  return {"node_id":s.c["node_id"],"firmware":FW,"provisioned":s.c["provisioned"],"network_id":s.c["network_id"],"pending_for_radio":sum(1 for r in s.sp if r.get("dir")=="r"),"pending_for_phone":sum(1 for r in s.sp if r.get("dir")=="p"),"spool_error":s.sp_error,"last_radio_at_ms":0}
 def configure(s,b,rid):
  t=str(b.get("provisioning_token") or "");expected=s.c.get("provisioning_token","")
  if s.c.get("provisioned") and t!=expected:return s.notify("ERROR",rid,body={"error":"provisioning_token_required"})
  if not s.c.get("provisioned") and t and t!=expected:return s.notify("ERROR",rid,body={"error":"invalid_provisioning_token"})
  n=dict(s.c);n["peers"]=list(b.get("peers") or [])[:32];n["network_id"]=str(b.get("network_id") or "")[:64];n["network_key"]=str(b.get("network_key") or "")[:128]
  pid=str(b.get("phone_id") or "")[:128]
  if pid:n["phone_id"]=pid
  n["provisioned"]=bool(n["network_id"] and n["network_key"]);s.c=n;config_save(n);s.radio_start();s.advertise();s.notify("CONFIG_ACK",rid,body=s.info())
 def has(s,mid,d):return any(r.get("id")==mid and r.get("dir")==d for r in s.sp)
 def put(s,wire,d):
  mid=str(wire.get("id",""))
  if s.sp_error or not mid:return False
  if s.has(mid,d):return True
  if len(s.sp)>=MAX_SPOOL:return False
  s.sp.append({"id":mid,"dir":d,"wire":wire,"e":wire.get("x")});spool_save(s.sp);return True
 def drop(s,mid,d):
  if not mid:return
  n=[r for r in s.sp if not(r.get("id")==mid and r.get("dir")==d)]
  if len(n)!=len(s.sp):
   s.sp=n;s.push.pop(d+mid,None);spool_save(s.sp)
 def note_try(s,key,now):
  v=s.push.get(key);s.push[key]=(now,1 if v is None else min(v[1]+1,8))
 def due(s,key,now,phone=False):
  v=s.push.get(key)
  if v is None:return True
  waits=(2000,5000,10000,30000,60000) if not phone else (1000,2000,5000,10000)
  return time.ticks_diff(now,v[0])>waits[min(v[1],len(waits)-1)]
 def from_phone(s,wire):
  mid=str(wire.get("id",""))
  if not s.c["provisioned"]:return s.notify("ERROR",mid,body={"error":"network_not_provisioned"})
  if s.sp_error:return s.notify("ERROR",mid,body={"error":"esp_spool_unreadable"})
  if not s.put(wire,"r"):return s.notify("ERROR",mid,body={"error":"esp_spool_full"})
  s.notify("LOCAL_STORED",mid,body={"message_id":mid})
  if not s.voice_busy():s.send_radio(wire);s.note_try("r"+mid,time.ticks_ms())
 def voice_busy(s):return time.ticks_diff(s.voice_until,time.ticks_ms())>0
 def voice_new(s,p):
  i=li(p)
  if i in s.lv:return False
  s.lv.append(i)
  if len(s.lv)>128:del s.lv[:32]
  return True
 def voice_send(s,p,t):
  if not s.radio:return
  q=lw(p,s.c["network_id"],s.c["network_key"],t)
  if not q:return
  for x in s.c["peers"] or ["ff:ff:ff:ff:ff:ff"]:
   try:s.radio.send(bytes.fromhex(x.replace(":","")),q)
   except Exception:pass
 def voice_phone(s,p,t):
  if not s.c["provisioned"] or not s.radio or len(p)>220:return
  s.voice_new(p);s.voice_until=time.ticks_add(time.ticks_ms(),350);s.voice_send(p,t)
 def voice_radio(s,p,t):
  if not s.voice_new(p):return
  s.last_radio=time.ticks_ms();s.voice_until=time.ticks_add(s.last_radio,350)
  if s.listen and s.conn:s.notify("LIVE_VOICE_RX",li(p),body={"packet":binascii.b2a_base64(p).strip().decode()})
  if t>1:s.voice_send(p,t-1)
 def send_radio(s,wire):
  if not s.radio:return
  try:packets=fragment(compact({"k":"D","w":wire}),s.c["network_id"],s.c["network_key"],getattr(espnow,"MAX_DATA_LEN",250))
  except Exception:return s.notify("RADIO_ERROR",str(wire.get("id","")),body={"error":"message_too_large"})
  for p in s.c["peers"] or ["ff:ff:ff:ff:ff:ff"]:
   peer=bytes.fromhex(p.replace(":",""))
   for e in packets:
    try:s.radio.send(peer,e)
    except Exception:pass
 def stored_ack(s,peer,mid):
  q={"methodmesh":2,"kind":"S","network_id":s.c["network_id"],"message_id":mid};q["auth"]=radio_auth(q,s.c["network_key"])
  try:s.radio.add_peer(peer)
  except Exception:pass
  try:s.radio.send(peer,compact(q).encode())
  except Exception:pass
 def radio_poll(s):
  if not s.radio:return
  try:
   peer,raw=s.radio.recv(0)
   if not raw:return
   if raw[:2]==b"MR":
    z=lu(raw,s.c["network_id"],s.c["network_key"])
    if z:s.voice_radio(z[1],z[0])
    return
   q=json.loads(raw.decode())
   if q.get("f")==1:
    t=s.fr.accept(q,s.c["network_id"],s.c["network_key"])
    if t:
     z=json.loads(t)
     if z.get("k")=="D" and wire_ok(z.get("w") or {}):s.remote_wire(peer,z["w"])
   elif q.get("kind")=="S" and q.get("network_id")==s.c["network_id"] and q.get("auth")==radio_auth(q,s.c["network_key"]):
    mid=str(q.get("message_id",""));s.last_radio=time.ticks_ms();s.drop(mid,"r");s.notify("REMOTE_STORED",mid,body={"message_id":mid})
  except Exception:pass
 def remote_wire(s,peer,wire):
  mid=str(wire.get("id",""));s.last_radio=time.ticks_ms();dk=str(wire.get("dk",""));dst=str(wire.get("d",""))
  if s.sp_error:return
  if dk=="mesh-phone" and (not s.c.get("phone_id") or dst!=s.c["phone_id"]):return
  if s.put(wire,"p"):
   s.stored_ack(peer,mid);s.push_wire(wire)
   if s.conn:s.note_try("p"+mid,time.ticks_ms())
 def push_wire(s,wire):
  if s.conn:s.notify("DATA",str(wire.get("id","")),wire=wire)
 def sync_push(s):
  now=time.ticks_ms()
  for r in s.sp:
   if r.get("dir")=="p":
    s.push_wire(r.get("wire") or {});s.note_try("p"+r.get("id",""),now)
 def maintain(s):
  now=time.ticks_ms()
  if s.voice_busy():return
  for r in list(s.sp):
   mid=r.get("id","")
   if r.get("dir")=="r" and s.due("r"+mid,now):
    s.send_radio(r.get("wire") or {});s.note_try("r"+mid,now);return
   elif r.get("dir")=="p" and s.conn and s.due("p"+mid,now,True):
    s.push_wire(r.get("wire") or {});s.note_try("p"+mid,now);return
n=Node();lm=time.ticks_ms()
while True:
 n.radio_poll();now=time.ticks_ms()
 if time.ticks_diff(now,lm)>=100:n.maintain();lm=now
 time.sleep_ms(10)
