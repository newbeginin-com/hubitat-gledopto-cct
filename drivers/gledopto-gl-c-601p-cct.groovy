metadata {
    definition(name: "GLEDOPTO GL-C-601P CCT", namespace: "newbeginin", author: "Brandon Ledford") {
        capability "Actuator"
        capability "Light"
        capability "Switch"
        capability "SwitchLevel"
        capability "ChangeLevel"
        capability "ColorTemperature"
        capability "ColorMode"
        capability "Refresh"
        capability "Configuration"
    }
    preferences {
        input name: "fadeSeconds", type: "decimal", title: "Default transition time (seconds)", defaultValue: 0.4
        input name: "minKelvin", type: "number", title: "Minimum color temperature (K)", defaultValue: 2000
        input name: "maxKelvin", type: "number", title: "Maximum color temperature (K)", defaultValue: 6329
        input name: "levelRate", type: "number", title: "Level change rate (1-254 steps per second)", defaultValue: 64
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def installed() {
    initState()
    sendEvent(name: "colorMode", value: "CT")
}

def updated() {
    log.info "${device.displayName} updated"
    initState()
    if (logEnable) runIn(1800, "logsOff")
}

def logsOff() {
    device.updateSetting("logEnable", [value: "false", type: "bool"])
    log.info "${device.displayName} debug logging disabled"
}

def configure() {
    log.info "${device.displayName} configure"
    initState()
    sendEvent(name: "colorMode", value: "CT")
    String d = device.deviceNetworkId
    String e = ep()
    String z = device.zigbeeId
    List cmds = [
        "zdo bind 0x${d} 0x${e} 0x01 0x0006 {${z}} {}", "delay 300",
        "zdo bind 0x${d} 0x${e} 0x01 0x0008 {${z}} {}", "delay 300",
        "zdo bind 0x${d} 0x${e} 0x01 0x0300 {${z}} {}", "delay 300"
    ]
    cmds += zigbee.configureReporting(0x0006, 0x0000, 0x10, 0, 3600, null)
    cmds += zigbee.configureReporting(0x0008, 0x0000, 0x20, 1, 3600, 1)
    cmds += zigbee.configureReporting(0x0300, 0x0007, 0x21, 1, 3600, 1)
    cmds += refreshCmds()
    send(cmds)
}

def refresh() {
    sendEvent(name: "colorMode", value: "CT")
    send(refreshCmds())
}

def on() {
    send([cmd(0x0006, 0x01, ""), "delay 500", rattr(0x0006, 0x0000)])
}

def off() {
    send([cmd(0x0006, 0x00, ""), "delay 500", rattr(0x0006, 0x0000)])
}

def setLevel(level, duration = null) {
    Integer pct = Math.max(0, Math.min(100, Math.round(level as Double) as Integer))
    if (pct == 0) {
        off()
        return
    }
    Integer tt = tenths(duration)
    send([
        cmd(0x0008, 0x04, "${hex2(pctToRaw(pct))} ${le16(tt)}"),
        "delay ${tt * 100 + 500}",
        rattr(0x0008, 0x0000), "delay 200",
        rattr(0x0006, 0x0000)
    ])
}

def setColorTemperature(kelvin, level = null, duration = null) {
    initState()
    Integer ctMin = state.ctMin as Integer
    Integer ctMax = state.ctMax as Integer
    Integer lo = Math.max((settings.minKelvin ?: 2000) as Integer, Math.ceil(1000000d / ctMax) as Integer)
    Integer hi = Math.min((settings.maxKelvin ?: 6329) as Integer, Math.floor(1000000d / ctMin) as Integer)
    Integer k = Math.max(lo, Math.min(hi, Math.round(kelvin as Double) as Integer))
    Integer mired = Math.max(ctMin, Math.min(ctMax, Math.round(1000000d / k) as Integer))
    Integer tt = tenths(duration)
    Integer lvl = level != null ? (Math.round(level as Double) as Integer) : null
    List cmds = []
    if (lvl != null && lvl > 0) {
        cmds += [cmd(0x0008, 0x04, "${hex2(pctToRaw(Math.min(100, lvl)))} ${le16(tt)}"), "delay 200"]
    } else if (device.currentValue("switch") != "on") {
        cmds += [cmd(0x0006, 0x01, ""), "delay 200"]
    }
    cmds += [
        cmd(0x0300, 0x0A, "${le16(mired)} ${le16(tt)}"),
        "delay ${tt * 100 + 500}",
        rattr(0x0300, 0x0007), "delay 200",
        rattr(0x0008, 0x0000), "delay 200",
        rattr(0x0006, 0x0000)
    ]
    sendEvent(name: "colorMode", value: "CT")
    send(cmds)
}

def startLevelChange(direction) {
    Integer rate = Math.max(1, Math.min(254, (settings.levelRate ?: 64) as Integer))
    if (direction == "up") {
        send([cmd(0x0008, 0x05, "00 ${hex2(rate)}")])
    } else {
        send([cmd(0x0008, 0x01, "01 ${hex2(rate)}")])
    }
}

def stopLevelChange() {
    send([cmd(0x0008, 0x03, ""), "delay 300", rattr(0x0008, 0x0000), "delay 200", rattr(0x0006, 0x0000)])
}

def parse(String description) {
    if (logEnable) log.debug "parse: ${description}"
    if (description?.startsWith("on/off:")) {
        evt("switch", description.trim().endsWith("1") ? "on" : "off", null)
        return null
    }
    Map m = zigbee.parseDescriptionAsMap(description)
    if (!m) return null
    if (m.attrId != null && m.value != null) handleAttr(m.cluster, m.attrId, m.value)
    m.additionalAttrs?.each { a ->
        if (a.attrId != null && a.value != null) handleAttr(m.cluster, a.attrId, a.value)
    }
    return null
}

private void handleAttr(String cluster, String attrId, String value) {
    String key = "${cluster}:${attrId}".toUpperCase()
    if (!(key in ["0006:0000", "0008:0000", "0300:0007", "0300:400B", "0300:400C"])) return
    Integer v
    try {
        v = Integer.parseInt(value, 16)
    } catch (e) {
        log.warn "${device.displayName} could not parse ${key} value ${value}"
        return
    }
    switch (key) {
        case "0006:0000":
            evt("switch", v == 1 ? "on" : "off", null)
            break
        case "0008:0000":
            evt("level", Math.max(1, Math.round(v * 100d / 254d) as Integer), "%")
            break
        case "0300:0007":
            if (v > 0) {
                Integer k = Math.round(1000000d / v) as Integer
                evt("colorTemperature", k, "°K")
                evt("colorName", genericName(k), null)
                evt("colorMode", "CT", null)
            }
            break
        case "0300:400B":
            if (v > 0) state.ctMin = v
            break
        case "0300:400C":
            if (v > 0) state.ctMax = v
            break
    }
}

private void evt(String name, value, String unit) {
    String desc = "${device.displayName} ${name} is ${value}${unit ?: ''}"
    if (txtEnable && device.currentValue(name)?.toString() != value.toString()) log.info desc
    sendEvent(name: name, value: value, unit: unit, descriptionText: desc)
}

private String genericName(Integer k) {
    if (k <= 2000) return "Sodium"
    if (k <= 2100) return "Starlight"
    if (k < 2400) return "Sunrise"
    if (k < 2800) return "Incandescent"
    if (k < 3300) return "Soft White"
    if (k < 3500) return "Warm White"
    if (k < 4150) return "Moonlight"
    if (k <= 5000) return "Horizon"
    if (k < 5500) return "Daylight"
    if (k < 6000) return "Electronic"
    if (k <= 6500) return "Skylight"
    return "Polar"
}

private void initState() {
    if (state.ctMin == null) state.ctMin = 158
    if (state.ctMax == null) state.ctMax = 500
}

private List refreshCmds() {
    [
        rattr(0x0006, 0x0000), "delay 200",
        rattr(0x0008, 0x0000), "delay 200",
        rattr(0x0300, 0x0007), "delay 200",
        rattr(0x0300, 0x400B), "delay 200",
        rattr(0x0300, 0x400C)
    ]
}

private Integer pctToRaw(Integer pct) {
    Math.max(1, Math.min(254, Math.round(pct * 2.54d) as Integer))
}

private Integer tenths(duration) {
    Double s = toSeconds(duration)
    if (s == null) s = toSeconds(settings.fadeSeconds)
    if (s == null) s = 0.4d
    Math.max(0, Math.min(65534, Math.round(s * 10) as Integer))
}

private Double toSeconds(v) {
    if (v == null || v.toString().trim() == "") return null
    try {
        return v.toString().trim() as Double
    } catch (e) {
        return null
    }
}

private String ep() {
    (device.endpointId ?: "0B").toString().toUpperCase().padLeft(2, "0")
}

private String cmd(Integer cluster, Integer command, String payload) {
    "he cmd 0x${device.deviceNetworkId} 0x${ep()} 0x${hex4(cluster)} 0x${hex2(command)} {${payload}}"
}

private String rattr(Integer cluster, Integer attrId) {
    "he rattr 0x${device.deviceNetworkId} 0x${ep()} 0x${hex4(cluster)} 0x${hex4(attrId)} {}"
}

private String hex2(Integer v) {
    String.format("%02X", v)
}

private String hex4(Integer v) {
    String.format("%04X", v)
}

private String le16(Integer v) {
    String h = hex4(v)
    h.substring(2) + h.substring(0, 2)
}

private void send(List cmds) {
    List out = cmds.collect { it.toString() }
    if (logEnable) log.debug "send: ${out}"
    sendHubCommand(new hubitat.device.HubMultiAction(out, hubitat.device.Protocol.ZIGBEE))
}
