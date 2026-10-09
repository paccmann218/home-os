"""Check the ESP32 room node from the laptop: read its sensors, then switch the LEDs.

Usage:  python tools/esp_test.py 192.168.1.50
The IP address is printed in the ESPHome logs right after the ESP32 joins Wi-Fi.
"""

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

SENSORS = [("sensor", "Room Temperature"), ("sensor", "Room Humidity"), ("binary_sensor", "Room Bright")]
DEMO_LED = "Demo Light"
BOARD_LED = "Board LED"


def entity_paths(domain: str, name: str) -> list[str]:
    # Newer ESPHome addresses entities by name; older versions by object id ("room_temperature").
    object_id = "".join(c if c.isalnum() else "_" for c in name.lower())
    return [f"/{domain}/{urllib.parse.quote(name)}", f"/{domain}/{object_id}"]


def call(host: str, domain: str, name: str, action: str = "", method: str = "GET", **params) -> dict:
    not_found = None
    for path in entity_paths(domain, name):
        url = f"http://{host}{path}" + (f"/{action}" if action else "")
        if params:
            url += "?" + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, method=method, data=b"" if method == "POST" else None)
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                body = resp.read()
                return json.loads(body) if body else {}
        except urllib.error.HTTPError as e:
            if e.code != 404:
                raise
            not_found = e
    raise not_found


def set_led(host: str, name: str, on: bool, **params) -> None:
    call(host, "light", name, "turn_on" if on else "turn_off", method="POST", **params)


def main() -> None:
    parser = argparse.ArgumentParser(description="Test the ESP32 room node.")
    parser.add_argument("host", help="ESP32 IP address, e.g. 192.168.1.50")
    host = parser.parse_args().host

    try:
        print("Sensors:")
        for domain, name in SENSORS:
            print(f"  {name:<18} {call(host, domain, name).get('state')}")

        print("\nBlinking the board LED and the demo LED 3 times...")
        for _ in range(3):
            for on in (True, False):
                set_led(host, BOARD_LED, on)
                set_led(host, DEMO_LED, on)
                time.sleep(0.5)

        print("Dimming the demo LED:")
        for pct in (10, 50, 100):
            start = time.perf_counter()
            set_led(host, DEMO_LED, True, brightness=round(pct * 2.55))
            print(f"  {pct:>3}%  (command took {(time.perf_counter() - start) * 1000:.0f} ms)")
            time.sleep(1.5)
        set_led(host, DEMO_LED, False)
    except urllib.error.URLError as e:
        sys.exit(f"\nCan't talk to http://{host}/ ({e}). Check the IP and that the laptop is on the same Wi-Fi.")

    print("\nDone. If both LEDs blinked and the readings look sensible, the room node works.")


if __name__ == "__main__":
    main()
