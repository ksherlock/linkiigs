/*
 * Created on Dec 9, 2006
 * Dec 9, 2006 4:16:27 PM
 */

import java.io.File;
import java.util.ArrayList;
import java.util.ListIterator;

import omf.*;

public class express
{

    private static void usage()
    {
        System.out.println("express v 0.1");
        System.out.println("Convert OMF file to ExpressLoad format.");
        System.out.println("usage: express [options] source");
        System.out.println("options");
        System.out.println("\t-d             dump express header");
        System.out.println("\t-o file        output file");
    }

    private static final int Read8(byte[] data, int offset)
    {
        return data[offset] & 0xff;
    }
    private static final int Read16(byte[] data, int offset)
    {
        int a,b;
        a = data[offset];
        b = data[offset+1];
        return (a & 0xff) 
            | ((b & 0xff) << 8);
    }
    private static final int Read32(byte[] data, int offset)
    {
        int a,b,c,d;
        a = data[offset];
        b = data[offset+1];
        c = data[offset+2];
        d = data[offset+3];
        
        return (a & 0xff) 
            | ((b & 0xff) << 8) 
            | ((c & 0xff) << 16)
            | ((d & 0xff) << 24);
    }
    
    private static void DumpExpress(File f)
    {
        ArrayList<OMF_Segment> segs;
        segs = OMF.LoadFile(f);
        if (segs == null)
        {
            System.err.printf("express: unable to open %1$s\n",
                    f.getName());
            return;
        }
        
        OMF_Segment seg = segs.get(0);
        if (seg.Kind() == OMF.KIND_DATA && seg.Attributes() == OMF.KIND_DYNAMIC)
        {
            
            String s = seg.SegmentName();
            s = s.trim();
            if (s.compareToIgnoreCase("~ExpressLoad") == 0 || s.compareToIgnoreCase("ExpressLoad") == 0)
            {
                ListIterator<OMF_Opcode>iter = seg.Opcodes();
                OMF_LConst lconst = null;
                int i = 0;
                // should have lconst & eof records only.
                for (;iter.hasNext();)
                {
                    i++;
                    OMF_Opcode op = iter.next();
                    if (op instanceof OMF_LConst) 
                        lconst = (OMF_LConst)op;
                }
                if (i == 2 && lconst != null)
                {
                    // ok, we can reasonably assume it's ok.
                    System.out.printf("%1$s:%2$s\n\n",
                            f.getName(), s);
                    
                    byte[] data = lconst.Data();
                    
                    int offset;
                    int segcnt = Read16(data, 4) + 1;
                    offset = 6;
                    
                    for (int j = 0; j < segcnt; j++)
                    {
                        int hoffset = offset + Read16(data, offset);
                        int lablen;
                        
                        System.out.printf("Flags:            $%1$04x\n",
                                Read16(data, offset + 2));
                                                
                        System.out.printf("LConst offset:    $%1$06x\n",
                                Read32(data, hoffset));
                        hoffset += 4;
                        
                        System.out.printf("LConst size:      $%1$06x\n",
                                Read32(data, hoffset));
                        hoffset += 4;
                        
                        System.out.printf("Reloc offset:     $%1$06x\n",
                                Read32(data, hoffset));
                        hoffset += 4;
                        
                        System.out.printf("Reloc size:       $%1$06x\n",
                                Read32(data, hoffset)); 
                        hoffset += 4;
                        
                        // local copy of OMF headers.
                        // reserved int8
                        hoffset++;
                        
                        System.out.printf("Label length:     $%1$02x\n",
                                lablen = Read8(data, hoffset ));
                        hoffset++;
                        
                        System.out.printf("Number length:    $%1$02x\n",
                                Read8(data, hoffset)); 
                        hoffset++;
                        
                        System.out.printf("Version:          $%1$02x\n",
                                Read8(data, hoffset));
                        hoffset++;
                        
                        System.out.printf("Bank size:        $%1$06x\n",
                                Read32(data, hoffset));
                        hoffset += 4;

                        System.out.printf("Kind:             $%1$04x\n",
                                Read16(data, hoffset));
                        hoffset+= 2;
                        
                        //reserved int16
                        hoffset+= 2;
                        
                        System.out.printf("Org:              $%1$06x\n",
                                Read16(data, hoffset));
                        hoffset += 4;
                        
                        System.out.printf("Align:            $%1$06x\n",
                                Read16(data, hoffset));
                        hoffset += 4;
                        
                        System.out.printf("Number sex:       $%1$02x\n",
                                Read8(data, hoffset));
                        hoffset++;
                        
                        //reserved int8
                        hoffset++;

                        System.out.printf("Segment number*:  $%1$04x\n",
                                Read16(data, 6 + (8 * segcnt) + (j * 2)));
                        
                        
                        System.out.printf("Segment number:   $%1$04x\n",
                                Read16(data, hoffset));
                        hoffset += 2;
                        
                        System.out.printf("Segment entry:    $%1$06x\n",
                                Read32(data, hoffset));
                        hoffset += 4;
                        
                        // name displacement
                        hoffset = hoffset + Read16(data, hoffset) - 0x28;
                        // loadname is 10 always 10 bytes.
                        // segment name is lablen bytes.
                        String x = new String(data, hoffset, 10);
                        hoffset += 10;
                        System.out.printf("Load name:        %1$s\n",
                                x);
                        
                        if (lablen == 0)
                        {
                            lablen = Read8(data, hoffset);
                            hoffset++;
                        }
                        if (lablen == 0) x = new String();
                        else x = new String(data, hoffset, lablen);
                        System.out.printf("Segment name:     %1$s\n",
                                x);                       
                        System.out.println();
                        offset += 8;
                        
                    }
                    
                    return;
                }
            }
        }
        
        System.err.printf("express: %1$s is not an ExpressLoad file\n",
                f.getName());
    }
    
    private static void Express(File in, File out)
    {
        ArrayList<OMF_Segment> segs;
        OMF_Segment seg;
        String sname;

        
        // out may be in, so 

        if (!in.exists())
        {
            System.err.printf("express: %1$s does not exist\n",
                    in.getName());
            return;
        }
        if (!in.isFile())
        {
            System.err.printf("express: %1$s is not a valid file\n",
                    in.getName());
            return;            
        }
        
       segs = OMF.LoadFile(in);
        
        if (segs == null)
        {
            System.err.printf("express: %1$s is not a valid OMF file\n",
                    in.getName());
            return;
        }
       
        
        
        /*
         * scan all segments for invalid opcodes.
         * only LCONST, DS (which will be converted to LCONST),
         * (c)RELOC (c)INTERSEG, SUPER, and EOF records are allowed.
         * 
         */
        
        /*
         * check if first segment is expressload. 
         */

        seg = segs.get(0);
        sname = seg.SegmentName();
        if (seg.Kind() == OMF.KIND_DATA && seg.Attributes() == OMF.KIND_DYNAMIC)   
        {
            if (sname.compareToIgnoreCase("~ExpressLoad") == 0
                    || sname.compareToIgnoreCase("ExpressLoad") == 0)
            {
                System.err.printf("express: %1$s is already in ExpressLoad format.\n",
                        in.getName());
                return;
            }
        }
        
        for (OMF_Segment s : segs)
        {
            // TODO -- check if jump table, act accordingly.
            // convert any DS records to LCONST records.
            s.Flatten();
            s.Normalize();
            boolean err = false;
            ListIterator<OMF_Opcode>iter = s.Opcodes();
            for (; iter.hasNext(); )
            {
                OMF_Opcode op = iter.next();
                
                switch (op.Opcode())
                {
                case OMF.OMF_INTERSEG:
                    // only expresable if file == 1
                    if (((OMF_Interseg)op).File() != 1)
                        err = true;
                    // TODO -- remap segnums.
                    break;
                case OMF.OMF_SUPER:
                    {
                        int type = ((OMF_Super)op).Type();
                        // INTERSEG2 -- INTERSEG12 refer to other files
                        // and are not permitted
                        
                        if (type >= OMF_Super.INTERSEG2 
                                && type <= OMF_Super.INTERSEG12)
                            err = true;
                        else
                        {
                            //CINTERSEG1, CINTERSEG13--24, CINTERSEG25--36 
                            //should be remapped.
                        }

                    }
                    break;
 
                case OMF.OMF_CINTERSEG:
                    // TODO -- remap seg nums
                case OMF.OMF_LCONST:
                case OMF.OMF_RELOC:
                case OMF.OMF_CRELOC:

                case OMF.OMF_EOF:
                    break;
                default:
                    err = true;                   
                } 
                
                if (err)
                {
                    System.err.printf("express: %1$s cannot be Expressed.\n",
                            in.getName());
                    return; 
                }
            }           
        }
        
        OMF_Segment eseg = new OMF_Segment();
        eseg.SetKind(OMF.KIND_DATA);
        eseg.SetAttributes(OMF.KIND_DYNAMIC);
        eseg.SetSegmentName("~ExpressLoad");
        
        
    }
    

    public static void main(String[] args)
    {
        int c;
        String outfile = null;
        boolean dflag = false;
        
        GetOpt go = new GetOpt(args, "do:h"); 
        
        while ((c = go.Next()) != -1)
        {
            switch(c)
            {
            case 'd':
                dflag = true;
                break;
            case 'o':
                outfile = go.Argument();
                break;
            case 'h':
            case '?':
                usage();
                return;
            }
        
        }
        
        args = go.CommandLine();
        
        if (args.length < 1)
        {
            usage();
            return;
        }
        
        for (String s : args)
        {
            File in, out;
            in = new File(s);
            out = outfile == null ? in : new File (outfile);
            
            if (dflag)
                DumpExpress(in);
            else
                Express(in, out);
            
            outfile = null;
            
        }
    }

    class ExpressEntryTable
    {
        public ExpressEntryTable()
        {
            offset =0;
            flags = 0;
            handle = 0;
        }
        int offset;
        int flags;
        int handle;
    }
    
    class ExpressHeaderTable
    {
        int lconst_off;
        int lconst_size;
        int reloc_off;
        int reloc_size;
        
    }
}
