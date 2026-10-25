// Project64 3.0.1-era API sampler for issue #35.
// Pinned API.js: https://github.com/project64/project64/blob/2df34341d95d28c536f78ea8174a8fdda066e9cf/Source/Project64/UserInterface/API.js
// API.js SHA-256: 8f08e4abd103f432c72ce445c75bd51b6688f9ead33a91541d1cb0c39c8554af.
// Set tracePath to a writable local file before running. The sampler reads only
// the specified candidates; it does not write emulated memory or send input.
var tracePath = "C:\\PATH\\TO\\LOCAL\\issue35-runtime-samples.jsonl";
var trialId = "trial-1";
var mario = 0x8033B170;
var counter = 0x8032D5D4;
var sequence = 0;
var limit = 1200;
var records = [];
var callbackId = null;
var finished = false;
function h32(v) {
    var s = (v >>> 0).toString(16);
    return "0x" + "00000000".substring(s.length) + s;
}
function capture() {
    if (finished) return;
    var record = {
        trialId: trialId,
        sequence: sequence++,
        hostUnixMs: Date.now(),
        pc: h32(gpr.pc),
        candidateCounterBefore: mem.u32[counter] >>> 0,
        marioInputCandidate: mem.u16[mario + 0x02],
        actionCandidate: h32(mem.u32[mario + 0x0C]),
        actionTimerCandidate: mem.u16[mario + 0x1A],
        positionCandidate: [mem.float[mario + 0x3C], mem.float[mario + 0x40], mem.float[mario + 0x44]],
        velocityCandidate: [mem.float[mario + 0x48], mem.float[mario + 0x4C], mem.float[mario + 0x50]],
        candidateCounterAfter: mem.u32[counter] >>> 0
    };
    record.candidateCounterConsistent = record.candidateCounterBefore === record.candidateCounterAfter;
    records.push(record);
    if (records.length >= limit) {
        finished = true;
        var payload = records.map(function(r) { return JSON.stringify(r); }).join("\n") + "\n";
        var buffer = new Buffer(payload.length);
        for (var i = 0; i < payload.length; i++) buffer[i] = payload.charCodeAt(i);
        var wrote = fs.writeFile(tracePath, buffer);
        events.remove(callbackId);
        console.log("issue35_capture_complete=" + JSON.stringify({trialId:trialId,count:records.length,writeResult:wrote,path:tracePath,first:records[0],last:records[records.length-1]}));
    }
}
callbackId = events.ondraw(capture);
console.log("issue35_capture_started=" + JSON.stringify({trialId:trialId,callbackId:callbackId,sampleLimit:limit,path:tracePath,clock:"host Date.now()",event:"Project64 events.ondraw"}));

