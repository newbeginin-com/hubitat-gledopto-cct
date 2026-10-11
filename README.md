# GLEDOPTO GL-C-601P CCT Driver for Hubitat

A CT-only (tunable white) Hubitat driver for the **GLEDOPTO GL-C-601P Zigbee 3.0 Pro Max 5-in-1 LED controller** running in CCT mode.

Hubitat pairs this controller as an RGB/RGBW bulb, and the stock and existing community drivers don't crossfade warm and cool white correctly: changing the color temperature mostly changes brightness, and the reported CT sticks around 6300K. This driver sends standard Zigbee color-temperature commands directly to the controller and exposes it as a clean tunable-white light, including in Apple Home.

## Features

- **Tunable white only:** Switch, Switch Level, Change Level, Color Temperature, Color Mode (always CT), Refresh, Configuration. No hue, saturation or RGB.
- **Independent dimming and color temperature:** level and CT are sent as separate commands.
- **Uses the controller's real CT range:** reads the physical min/max (158–500 mireds, about 2000K–6329K) and limits requests to it, with optional min/max Kelvin preferences.
- **Accurate state:** attributes come from the device's own reports, not assumed values.
- **Clean Apple Home exposure:** shows as tunable white with no color wheel.

## Installation

1. In Hubitat, go to **Drivers Code → New Driver → Import**.
2. Paste this URL and click **Import**, then **Save**:

   ```
   https://raw.githubusercontent.com/newbeginin-com/hubitat-gledopto-cct/main/drivers/gledopto-gl-c-601p-cct.groovy
   ```

3. Open your device, change **Type** to **GLEDOPTO GL-C-601P CCT**, and click **Save Device**.
4. Click **Configure**.
5. Click **Save Preferences** once.

### Updating

Open the driver in **Drivers Code**, click **Import** (the URL is pre-filled), then **Save**.

## Preferences

| Setting | Default | Description |
|---|---|---|
| Default transition time (seconds) | 0.4 | Fade time when a command doesn't specify one |
| Minimum color temperature (K) | 2000 | Lowest Kelvin the driver will send |
| Maximum color temperature (K) | 6329 | Highest Kelvin the driver will send |
| Level change rate | 64 | Steps per second for Start/Stop Level Change (1–254) |
| Enable debug logging | On | Turns off automatically after 30 minutes |
| Enable descriptionText logging | On | Logs state changes |

## Apple Home (Maker API → Homebridge)

If you expose Hubitat devices to Apple Home through Maker API and Homebridge, and the device was already in Homebridge under an RGB driver, Home keeps the old color-light definition. To refresh it:

1. Remove the device from your Maker API instance.
2. Restart Homebridge.
3. Add the device back to Maker API.
4. Restart Homebridge again.

It will come back as a tunable-white light. You'll need to reassign its room and any Home scenes or automations.

## Background: what was wrong

Diagnostics on the controller showed:

- It reports a model ID of **GL-C-007P / GL-C-008P** (RGBW / RGB+CCT) and advertises full color capabilities (`0x400A = 0x1F`), even with the indicator set to green/CCT. Changing the mode with the Opt button and re-pairing didn't change this.
- It exposes a **single endpoint (0x0B)**.
- A standard ZCL **Move to Color Temperature** command (cluster `0x0300`, command `0x0A`) sent to endpoint `0x0B` crossfades correctly at constant brightness, and the controller reports Current Color Temperature (`0x0007`) accurately.

The hardware and firmware handle CCT correctly. Hubitat just needed a driver that treats the controller as a CT-only light.

## Notes

- Tested on one controller (firmware build `30151243`) with CW/WW wired to the V2/V3 terminals. Reports from other GL-C-601P owners are welcome.
- At 500 mireds the strip runs 100% on its warm-white channel. On a 2700K strip, "2000K" is really just its warmest setting, so Kelvin values at the warm end are approximate.
- If a previous driver left a `transitionTime` setting on the device, it won't interfere. This driver uses its own setting name.

## Support

Questions and feedback: [Hubitat Community thread](https://community.hubitat.com/t/release-gledopto-gl-c-601p-pro-max-5-in-1-cct-tunable-white-driver/166746)

Issues and bug reports: [GitHub Issues](https://github.com/newbeginin-com/hubitat-gledopto-cct/issues)

## License

MIT. See [LICENSE](LICENSE).
