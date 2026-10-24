import java.lang.reflect.*;
import java.util.*;
import java.io.*;
public class Minecraft1122RegistryProbe {
 static Object field(String cn,String fn)throws Exception{return Class.forName(cn).getField(fn).get(null);}
 static Iterator<?> iterator(Object reg)throws Exception{return (Iterator<?>)reg.getClass().getMethod("iterator").invoke(reg);}
 static Object key(Object reg,Object v)throws Exception{return reg.getClass().getMethod("b",Object.class).invoke(reg,v);}
 static int id(Object reg,Object v)throws Exception{return ((Number)reg.getClass().getMethod("a",Object.class).invoke(reg,v)).intValue();}
 public static void main(String[] args)throws Exception{
  Class.forName("ni").getMethod("c").invoke(null); Class.forName("air");
  Object items=field("ain","g"), blocks=field("aow","h");
  Method itemForBlock=Class.forName("ain").getMethod("a",Class.forName("aow"));
  int ni=0,nb=0,nbi=0;
  try(PrintWriter out=new PrintWriter(new FileWriter(args[0]))){out.println("kind,numeric_id,registry_name,block_numeric_id,block_registry_name");
   for(Iterator<?> it=iterator(items);it.hasNext();){Object v=it.next();out.printf("item,%d,%s,,%n",id(items,v),key(items,v));ni++;}
   for(Iterator<?> it=iterator(blocks);it.hasNext();){Object b=it.next();String bk=key(blocks,b).toString();int bid=id(blocks,b);out.printf("block,%d,%s,,%n",bid,bk);nb++;
    Object iv=itemForBlock.invoke(null,b);if(iv!=null){out.printf("block-item,%d,%s,%d,%s%n",id(items,iv),key(items,iv),bid,bk);nbi++;}
   }
  }
  System.err.println("items="+ni+" blocks="+nb+" Item.getItemFromBlock mappings="+nbi);
 }
}


