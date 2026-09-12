import json,hashlib,binascii
MX=40000;MS=512;MD=160

def c(v):return json.dumps(v).replace(": ",":").replace(", ",",")
def hx(b):return binascii.hexlify(b).decode()
def sh(b):return hashlib.sha256(b).digest()
def hm(k,m):
 k=k.encode() if isinstance(k,str) else k;m=m.encode() if isinstance(m,str) else m
 if len(k)>64:k=sh(k)
 k=k+b"\0"*(64-len(k));o=bytes(x^0x5c for x in k);i=bytes(x^0x36 for x in k)
 return hx(sh(o+sh(i+m)))
def nt(n):return hx(sh(n.encode()))[:8]
def ap(p,k):
 q=dict(p);q.pop("auth",None);q.pop("a",None);return hm(k,c(q))[:24]
def at(t,k):return hm(k,t)[:24]

def fragment(t,n,k,m):
 r=hx(sh(t.encode()))[:12];a=at(t,k);g=nt(n);o=[];p=0;s=0
 while p<len(t):
  z=min(104,len(t)-p)
  while z>8:
   d=t[p:p+z];q={"f":1,"n":g,"i":r,"s":s,"z":1 if p+z>=len(t) else 0,"d":d,"a":a};e=c(q).encode()
   if len(e)<=m:break
   z-=8
  if z<=8:raise ValueError("fragment too large")
  o.append(e);p+=z;s+=1
 return o

class Reassembler:
 def __init__(s):s.x={}
 def accept(s,q,n,k):
  if q.get("f")!=1 or q.get("n")!=nt(n):return None
  r=str(q.get("i",""));i=int(q.get("s",-1));d=str(q.get("d",""))
  if not r or i<0 or i>=MS or len(d)>MD:return None
  v=s.x.get(r)
  if v is None:
   if len(s.x)>=8:s.x={}
   v={"p":{},"l":-1,"a":str(q.get("a",""))};s.x[r]=v
  old=v["p"].get(i,"")
  if sum(len(x) for x in v["p"].values())-len(old)+len(d)>MX:
   s.x.pop(r,None);return None
  v["p"][i]=d
  if q.get("z"):v["l"]=i
  l=v["l"]
  if l<0 or any(j not in v["p"] for j in range(l+1)):return None
  t="".join(v["p"][j] for j in range(l+1));del s.x[r]
  return t if at(t,k)==v["a"] else None
