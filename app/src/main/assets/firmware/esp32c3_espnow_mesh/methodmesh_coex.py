try:
 import bluetooth
except ImportError:
 import ubluetooth as bluetooth
try:
 import network,espnow
except ImportError:
 network=None;espnow=None

RADIO_CHANNEL=6
radio=None
ble=None
channel=0
rxbuf=0
error=""

if network is not None and espnow is not None:
 try:
  wlan=network.WLAN(network.STA_IF);wlan.active(True)
  try:wlan.disconnect()
  except Exception:pass
  wlan.config(channel=RADIO_CHANNEL)
  try:channel=int(wlan.config("channel"))
  except Exception:channel=RADIO_CHANNEL
  radio=espnow.ESPNow();radio.config(rxbuf=528);radio.active(True);rxbuf=528
 except Exception as x:
  radio=None;channel=0;error="radio: "+str(x)
try:
 ble=bluetooth.BLE();ble.active(True)
except Exception as x:
 error=(error+"; " if error else "")+"ble: "+str(x)
