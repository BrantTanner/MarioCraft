param([Parameter(Mandatory=$true)][int]$TargetPid,[Parameter(Mandatory=$true)][string]$Project64Path,[Parameter(Mandatory=$true)][string]$AnalysisRomPath)
$ErrorActionPreference = 'Stop'
$exe = [IO.Path]::GetFullPath($Project64Path)
$rom = [IO.Path]::GetFullPath($AnalysisRomPath)
$expectedExeHash = 'C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8'
$expectedRomHash = '17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91'
$p = Get-Process -Id $TargetPid -ErrorAction Stop
if ([IO.Path]::GetFullPath($p.Path) -ne [IO.Path]::GetFullPath($exe)) { throw 'PID is not the scoped portable Project64 executable.' }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $exe).Hash -ne $expectedExeHash) { throw 'Project64 executable hash mismatch.' }
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $rom).Hash -ne $expectedRomHash) { throw 'Analysis-copy ROM hash mismatch.' }
$romBytes = [IO.File]::ReadAllBytes($rom)
$romPrefix = [byte[]]$romBytes[0x1000..0x10FF]
if (-not ('MarioCraftReadOnlyProcessMemory' -as [type])) {
  Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class MarioCraftReadOnlyProcessMemory {
  [StructLayout(LayoutKind.Sequential)] public struct MBI {
    public IntPtr BaseAddress; public IntPtr AllocationBase; public uint AllocationProtect;
    public ushort PartitionId; public IntPtr RegionSize; public uint State; public uint Protect; public uint Type;
  }
  [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr OpenProcess(uint access, bool inherit, int pid);
  [DllImport("kernel32.dll", SetLastError=true)] public static extern bool CloseHandle(IntPtr h);
  [DllImport("kernel32.dll", SetLastError=true)] public static extern bool ReadProcessMemory(IntPtr h, IntPtr address, byte[] buffer, IntPtr size, out IntPtr read);
  [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr VirtualQueryEx(IntPtr h, IntPtr address, out MBI info, IntPtr length);
  static byte[] ReverseWords(byte[] x) { byte[] y=(byte[])x.Clone(); for(int i=0;i+3<y.Length;i+=4){byte a=y[i],b=y[i+1];y[i]=y[i+3];y[i+1]=y[i+2];y[i+2]=b;y[i+3]=a;} return y; }
  static byte[] SwapHalfwords(byte[] x) { byte[] y=(byte[])x.Clone(); for(int i=0;i+3<y.Length;i+=4){byte a=y[i];y[i]=y[i+1];y[i+1]=a;a=y[i+2];y[i+2]=y[i+3];y[i+3]=a;} return y; }
  static bool Match(byte[] a,int off,byte[] b) { if(off<0||off+b.Length>a.Length)return false;for(int i=0;i<b.Length;i++)if(a[off+i]!=b[i])return false;return true; }
  static uint U32(byte[] b,int i,int mode) { if(mode==0)return (uint)(b[i+3]|b[i+2]<<8|b[i+1]<<16|b[i]<<24);if(mode==2)return (uint)(b[i+2]|b[i+3]<<8|b[i]<<16|b[i+1]<<24);return (uint)(b[i]|b[i+1]<<8|b[i+2]<<16|b[i+3]<<24); }
  static float F32(byte[] b,int i,int mode) { byte[] v=new byte[4];Array.Copy(b,i,v,0,4);if(mode==0){byte a=v[0],c=v[1];v[0]=v[3];v[1]=v[2];v[2]=c;v[3]=a;}else if(mode==2){byte a=v[0],c=v[1];v[0]=v[2];v[1]=v[3];v[2]=a;v[3]=c;}return BitConverter.ToSingle(v,0); }
  static bool InRdram(uint p) { return p>=0x80000000u && p<0x80400000u; }
  static bool Read(IntPtr h,long addr,byte[] b) { IntPtr n;return ReadProcessMemory(h,new IntPtr(addr),b,new IntPtr(b.Length),out n)&&n.ToInt64()==b.Length; }
  public static string Scan(int pid, byte[] romPrefix, long maxBytes) {
    const uint PROCESS_VM_READ=0x0010,PROCESS_QUERY_INFORMATION=0x0400;
    const uint MEM_COMMIT=0x1000,PAGE_GUARD=0x100,PAGE_NOACCESS=0x01;
    IntPtr h=OpenProcess(PROCESS_VM_READ|PROCESS_QUERY_INFORMATION,false,pid); if(h==IntPtr.Zero)throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error(),"OpenProcess read-only failed");
    long scanned=0, regions=0, signatures=0, rejected=0;var results=new List<string>();
    try {
      long address=0x10000;long ceiling=IntPtr.Size==8?0x00007FFFFFFEFFFF:0x7FFEFFFF;
      byte[][] patterns={romPrefix,ReverseWords(romPrefix),SwapHalfwords(romPrefix)};
      while(address<ceiling && scanned<maxBytes && results.Count<32) {
        MBI mbi;IntPtr n=VirtualQueryEx(h,new IntPtr(address),out mbi,new IntPtr(Marshal.SizeOf(typeof(MBI))));
        if(n==IntPtr.Zero) { address+=0x10000;continue; }
        long start=mbi.BaseAddress.ToInt64(), size=mbi.RegionSize.ToInt64(), end=start+size;
        if(size<=0||end<=address){address+=0x10000;continue;}
        bool readable=mbi.State==MEM_COMMIT && (mbi.Protect&PAGE_GUARD)==0 && (mbi.Protect&0xFF)!=PAGE_NOACCESS && ((mbi.Protect&0xFF)==0x02||(mbi.Protect&0xFF)==0x04||(mbi.Protect&0xFF)==0x08||(mbi.Protect&0xFF)==0x20||(mbi.Protect&0xFF)==0x40||(mbi.Protect&0xFF)==0x80);
        if(readable) {
          regions++;long pos=start;const int chunkSize=1<<20;
          while(pos<end && scanned<maxBytes && results.Count<32) {
            int want=(int)Math.Min(chunkSize+255,end-pos);if(want<patterns[0].Length)break;byte[] buf=new byte[want];IntPtr got;
            bool ok=ReadProcessMemory(h,new IntPtr(pos),buf,new IntPtr(want),out got);int count=ok?(int)got.ToInt64():0;
            if(count<want){int done=0;count=0;while(done<want){int nbytes=Math.Min(4096,want-done);byte[] page=new byte[nbytes];IntPtr pageGot;bool pageOk=ReadProcessMemory(h,new IntPtr(pos+done),page,new IntPtr(nbytes),out pageGot);int pageRead=pageOk?(int)pageGot.ToInt64():0;if(pageRead==nbytes){Array.Copy(page,0,buf,done,pageRead);done+=pageRead;count=done;}else{done+=nbytes;}}}
            if(count>=patterns[0].Length){for(int i=0;i<=count-patterns[0].Length;i++){
              int mode=-1;for(int m=0;m<patterns.Length;m++)if(Match(buf,i,patterns[m])){mode=m;break;}if(mode<0)continue;signatures++;
              long hostBase=pos+i-0x246000;if(hostBase<0)continue;long mario=hostBase+0x33B170;byte[] mb=new byte[0xA0];if(!Read(h,mario,mb))continue;
              uint action=U32(mb,0x0C,mode),ctrl=U32(mb,0x9C,mode),floor=U32(mb,0x68,mode);
              float mx=F32(mb,0x3C,mode),my=F32(mb,0x40,mode),mz=F32(mb,0x44,mode),speed=F32(mb,0x54,mode);
              uint area=U32(mb,0x90,mode);bool fieldsOk=InRdram(ctrl)&&InRdram(floor)&&InRdram(area)&&!Single.IsNaN(mx)&&!Single.IsNaN(my)&&!Single.IsNaN(mz)&&!Single.IsInfinity(mx)&&!Single.IsInfinity(my)&&!Single.IsInfinity(mz);if(!fieldsOk)rejected++;
              byte[] cbuf=new byte[0x18];if(!Read(h,hostBase+0x33AF90,cbuf))continue;
              byte[] counter=new byte[4];if(!Read(h,hostBase+0x32D5D4,counter))continue;
              uint ctr=U32(counter,0,mode);
              results.Add(String.Format("{{\"hostBase\":\"0x{0:X}\",\"romMatchHost\":\"0x{1:X}\",\"mode\":{2},\"fieldsValid\":{3},\"action\":\"0x{4:X8}\",\"controllerPointer\":\"0x{5:X8}\",\"floorPointer\":\"0x{6:X8}\",\"areaPointer\":\"0x{7:X8}\",\"x\":{8:R},\"y\":{9:R},\"z\":{10:R},\"forwardSpeed\":{11:R},\"counter\":{12},\"counterExpected\":{13},\"romPrefixMatch\":true}}",hostBase,pos+i,mode,fieldsOk.ToString().ToLowerInvariant(),action,ctrl,floor,area,mx,my,mz,speed,ctr,(ctr==41228).ToString().ToLowerInvariant()));
              break;
            }}
            scanned+=count;pos+=chunkSize;
          }
        }
        address=end;
      }
      return String.Format("{{\"pid\":{0},\"regions\":{1},\"bytesScanned\":{2},\"signatureHits\":{3},\"rejectedFieldCandidates\":{4},\"candidates\":[{5}]}}",pid,regions,scanned,signatures,rejected,String.Join(",",results));
    } finally { CloseHandle(h); }
  }
}
'@
}
$rawScan = [MarioCraftReadOnlyProcessMemory]::Scan($TargetPid, $romPrefix, 1073741824) | ConvertFrom-Json
$p = Get-Process -Id $TargetPid -ErrorAction Stop
[pscustomobject]@{
  probeUtc=[DateTime]::UtcNow.ToString('o'); processId=$TargetPid; processStartUtc=$p.StartTime.ToUniversalTime().ToString('o')
  executable=$exe; executableSha256=$expectedExeHash; romAnalysisCopy=$rom; romSha256=$expectedRomHash
  mapping='ROM file offset 0x1000 loads at RDRAM 0x80246000; candidate hostBase = signatureAddress - 0x246000; Mario guest 0x8033B170'
  validatedMode='mode 1: host words reversed per 32-bit word; native little-endian u32/f32, u16 guest offset XOR 2, u8 guest offset XOR 3 (u16/u8 still to validate)'
  scan=$rawScan
} | ConvertTo-Json -Depth 8 -Compress
