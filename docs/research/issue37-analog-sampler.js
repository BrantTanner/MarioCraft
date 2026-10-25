// Read-only, bounded Project64 sampler for the SM64 USA controller record.
// Stop stale Running scripts. Set a fresh tracePath and unique timestamped trialId,
// click Run, verify the fresh started marker, then apply guarded helper inputs.
// This reads emulated memory; it never writes to the game or controller state.
var tracePath = "C:\\TEMP\\issue37-range100.jsonl";
var trialId = "issue37-range100";
var rangePercent = 100;
var direction = "up-down-left-right-diagonals";
var mario = 0x8033B170;
var counter = 0x8032D5D4;
var sequence = 0;
var limit = 7200;
var callbackLimit = 14400;
var callbackInvocations = 0;
var invalidPointerCallbacks = 0;
var records = [];
var callbackId = 0;
var finished = false;
var captureStartedUnixMs = Date.now();

function h32(v) {
    var s = (v >>> 0).toString(16);
    return "0x" + "00000000".substring(s.length) + s;
}

function finish(reason) {
    if (finished) return;
    finished = true;
    var payload = records.map(function(r) { return JSON.stringify(r); }).join("\n");
    if (payload.length > 0) payload += "\n";
    var buffer = new Buffer(payload.length);
    for (var i = 0; i < payload.length; i++) buffer[i] = payload.charCodeAt(i);
    var wrote = fs.writeFile(tracePath, buffer);
    console.log("issue37_analog_capture_complete=" + JSON.stringify({
        trialId: trialId,
        reason: reason,
        count: records.length,
        callbackInvocations: callbackInvocations,
        invalidPointerCallbacks: invalidPointerCallbacks,
        writeResult: wrote,
        path: tracePath,
        first: records.length ? records[0] : null,
        last: records.length ? records[records.length - 1] : null
    }));
    events.remove(callbackId);
}

function capture() {
    if (finished) return;
    callbackInvocations++;
    var c = mem.u32[mario + 0x9C] >>> 0;
    if (c < 0x80000000 || c >= 0x80400000) {
        invalidPointerCallbacks++;
        if (callbackInvocations >= callbackLimit) finish("callback-limit");
        return;
    }

    var before = mem.u32[counter] >>> 0;
    var rawX = mem.s16[c];
    var rawY = mem.s16[c + 2];
    var processedX = mem.float[c + 4];
    var processedY = mem.float[c + 8];
    var magnitude = mem.float[c + 0x0C];
    var buttonsDown = mem.u16[c + 0x10];
    var buttonsPressed = mem.u16[c + 0x12];
    var after = mem.u32[counter] >>> 0;

    records.push({
        trialId: trialId,
        controllerPointer: h32(c),
        rangePercent: rangePercent,
        direction: direction,
        sequence: sequence++,
        hostUnixMs: Date.now(),
        loopCounterBefore: before,
        rawX: rawX,
        rawY: rawY,
        processedX: processedX,
        processedY: processedY,
        magnitude: magnitude,
        buttonsDown: buttonsDown,
        buttonsPressed: buttonsPressed,
        loopCounterAfter: after,
        counterPairEqual: before === after
    });

    if (records.length >= limit) finish("sample-limit");
    else if (callbackInvocations >= callbackLimit) finish("callback-limit");
}

callbackId = events.ondraw(capture);
console.log("issue37_analog_capture_started=" + JSON.stringify({
    trialId: trialId,
    captureStartedUnixMs: captureStartedUnixMs,
    rangePercent: rangePercent,
    direction: direction,
    callbackId: callbackId,
    sampleLimit: limit,
    callbackLimit: callbackLimit,
    event: "Project64 events.ondraw",
    clock: "host Date.now()"
}));
