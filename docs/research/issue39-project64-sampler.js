// Copy this read-only sampler into Project64 `Scripts`; output is a local JSONL beside the emulator process working directory.
// Edit trialId/profile for each run. It reads memory only and caps one capture at 7200 rows.
// Bounded, read-only issue #39 sampler. Use the verified Project64 clone only.
var tracePath = "C:\\path\\to\\local\\issue39-run.jsonl";
var trialId = "replace-with-trial-id";
var profile = {plugin:"NRage",realN64Range:true,stickRange:100,padDeadZone:5};
var mario = 0x8033B170;
var counter = 0x8032D5D4;
var sequence = 0;
var callbacks = 0;
var limit = 7200;
var callbackLimit = 14400;
var records = [];
var callbackId = 0;
var finished = false;
function h32(v) { var s=(v>>>0).toString(16); return "0x"+"00000000".substring(s.length)+s; }
function capture() {
    if (finished) return;
    callbacks++;
    var c=mem.u32[mario+0x9C]>>>0;
    if(c<0x80000000 || c>=0x80400000) { if(callbacks>=callbackLimit) events.remove(callbackId); return; }
    var before=mem.u32[counter]>>>0;
    var row={trialId:trialId,sequence:sequence++,hostUnixMs:Date.now(),
      controllerPointer:h32(c),profile:profile,
      loopCounterBefore:before,
      rawX:mem.s16[c],rawY:mem.s16[c+2],processedX:mem.float[c+4],processedY:mem.float[c+8],inputMagnitude:mem.float[c+0x0C],
      buttonsDown:mem.u16[c+0x10],buttonsPressed:mem.u16[c+0x12],
      previousAction:h32(mem.u32[mario+0x10]),action:h32(mem.u32[mario+0x0C]),actionState:mem.u16[mario+0x18],actionArgument:mem.u32[mario+0x1C],actionTimer:mem.u16[mario+0x1A],
      doubleJumpTimerCandidate:mem.u8[mario+0x2B],squishTimerCandidate:mem.u8[mario+0xB4],flagsCandidate:mem.u32[mario+0x04],
      intendedMagnitude:mem.float[mario+0x20],quicksandDepth:mem.float[mario+0xC0],slopeDecelThreshold:mem.float[0x80336AD0],intendedYaw:mem.s16[mario+0x24],faceYaw:mem.s16[mario+0x2E],
      x:mem.float[mario+0x3C],y:mem.float[mario+0x40],z:mem.float[mario+0x44],
      velocityX:mem.float[mario+0x48],velocityY:mem.float[mario+0x4C],velocityZ:mem.float[mario+0x50],forwardSpeed:mem.float[mario+0x54],
      floorPointer:h32(mem.u32[mario+0x68])};
    var f=mem.u32[mario+0x68]>>>0;
    if(f>=0x80000000 && f<0x80400000) { row.floorType=mem.s16[f];row.floorNormalY=mem.float[f+0x20]; }
    row.loopCounterAfter=mem.u32[counter]>>>0;row.counterPairEqual=row.loopCounterBefore===row.loopCounterAfter;
    records.push(row);
    if(records.length % 30 === 0) {
      var progress=records.slice(-20).map(function(r){return JSON.stringify(r);}).join("\n")+"\n";
      var progressBuffer=new Buffer(progress.length);
      for(var p=0;p<progress.length;p++) progressBuffer[p]=progress.charCodeAt(p);
      fs.writeFile(tracePath.replace(/\.jsonl$/, ".progress.jsonl"),progressBuffer);
    }
    if(records.length>=limit || callbacks>=callbackLimit) {
      finished=true;
      var payload=records.map(function(r){return JSON.stringify(r);}).join("\n")+"\n";
      var buffer=new Buffer(payload.length);
      for(var i=0;i<payload.length;i++) buffer[i]=payload.charCodeAt(i);
      var result=fs.writeFile(tracePath,buffer);
      console.log("issue39_capture_complete="+JSON.stringify({trialId:trialId,count:records.length,callbacks:callbacks,writeResult:result,path:tracePath,first:records[0],last:records[records.length-1]}));
      events.remove(callbackId);
    }
}
callbackId=events.ondraw(capture);
console.log("issue39_capture_started="+JSON.stringify({trialId:trialId,profile:profile,callbackId:callbackId,sampleLimit:limit,callbackLimit:callbackLimit,output:tracePath,event:"Project64 events.ondraw",clock:"host Date.now()"}));




