import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import omf.*;

/*
 * Created on Feb 1, 2006
 *
 */

/**
 * @author Kelvin
 * 
 */
public class Linker
{

    public static final int FLAG_VERBOSE = 1;

    public static final int FLAG_EXPRESS = 2;
    public static final int FLAG_RTL = 4;
    public static final int FLAG_BIN = 8;
    public static final int FLAG_MAP = 16;
    public static final int FLAG_RELOAD = 32;

    // a hashtable of all the LOCAL/GLOBAL/EQU/GEQU symbols.
    private SymbolTable fGlobal;

    private HashSet<String> fStrong;

    private ArrayList<Symbol> fExpr;

    private HashSet<String> fMissing;

    private HashSet<String> fUsing;

    // private HashMap fSegments;
    private HashArray<SegmentData> fSegments;

    private int fFlags;
    
    private OMF_Data fJumpTable;
    private OMF_Data fPathName;
    private OMF_Segment fExtra;
    
    private int fOrg;
    private int fFileno;

    public Linker(int flags, int org)
    {
        /*
         * Globally defined symbols.
         */
        fGlobal = new SymbolTable();

        /*
         * all STRONG references. not yet implemented.
         */
        fStrong = new HashSet<String>();
        /*
         * missing symbols.
         */
        fMissing = new HashSet<String>();
        /*
         * all expressions in the omf file.
         */
        fExpr = new ArrayList<Symbol>();
        /*
         * segments.
         */
        fSegments = new HashArray<SegmentData>();
        /*
         * all USING segments ... not yet implemented.
         */
        fUsing = new HashSet<String>();
        fFlags = flags;
        fOrg = org;
        fJumpTable = new OMF_Data();
        fPathName = new OMF_Data();
        fExtra = null;
        fFileno = 1;
    }

    public void ProcessFile(File f)
    {

        SymbolTable local = new SymbolTable();
        ArrayList<OMF_Segment> segments;

        segments = OMF.LoadFile(f);
        if (segments == null)
        {
            System.out.print(f.getName() + " is not a valid OMF file.");
            return;
        }

        for (Iterator<OMF_Segment> iter = segments.iterator(); iter.hasNext();)
        {
            this.ProcessSegment(iter.next(), local, fUsing, fExpr);
        }
    }

    public boolean Save(File f)
    {
        boolean ret = true;
        FileOutputStream io;
        
       if ((fFlags & FLAG_BIN) != 0)
       {
           return SaveBin(f);
       }
        
        try
        {
            io = new FileOutputStream(f);

            fJumpTable.AppendData(8, (byte)0);
            ArrayList<OMF_Segment> sorted = Sort();
            Resolve();
            fJumpTable.AppendData(4, (byte) 0);
                       
            for (int i = 0; i < sorted.size(); i++)
            {
                OMF_Segment omf = sorted.get(i);
                omf.AddOpcode(new OMF_Eof());
                ret = omf.Save(io);
                if (!ret)
                    break;
            }
            io.close();
        } catch (Exception e)
        {
            ret = false;
        }

        return ret;
    }
    
    private boolean SaveBin(File f)
    {
        FileOutputStream io;
        Resolve();
        
        
        // there should only be one segment, it should be static, and 
        //we should save it.
        
        if (fSegments.size() != 1)
        {
            System.out.println("Binary files must have 1 segment.");
            return false;
        }
        
        SegmentData sd = fSegments.get(0);
        OMF_Data data = sd.data;
        
        try
        {
            io = new FileOutputStream(f);
            io.write(data.Data(), 0, data.CodeSize());
        }
        catch (Exception e)
        {
            return false;
        }
        
        
        return true;
    }


    /*
     * sort the segments for expressing, 
     * add in the expressload and jump table segments if needed.
     */
    protected ArrayList<OMF_Segment> Sort()
    {
        ArrayList<OMF_Segment> sorted = new ArrayList<OMF_Segment>();
        int segnum = 1;
        
        /*
         * move all static segments to the start....
         */
        if ((fFlags & FLAG_RTL) != 0)
        {
            fFlags &= (~FLAG_EXPRESS);
            fExtra = new OMF_Segment();
            fExtra.SetSegmentName("~Library");
            fExtra.SetSegmentNumber(segnum++);
            fExtra.SetKind(OMF.KIND_LIBDICT);
            sorted.add(fExtra);
        }
        if ((fFlags & FLAG_EXPRESS) != 0)
        {
            fExtra = new OMF_Segment();
            fExtra.SetSegmentName("~ExpressLoad");
            fExtra.SetSegmentNumber(segnum++);
            fExtra.SetKind(OMF.KIND_DATA);
            fExtra.SetAttributes(OMF.KIND_DYNAMIC);
            sorted.add(fExtra);
        }

        
        boolean dynamic = false;
        // phase 1 -- move all static segments to the sorted list.
        for (int i = 0; i < fSegments.size(); i++)
        {
            OMF_Segment omf = fSegments.get(i).omf;
            
            if ((omf.Attributes() & OMF.KIND_DYNAMIC) == OMF.KIND_DYNAMIC)
            {
                dynamic = true;
                continue;
            }
            omf.SetSegmentNumber(segnum++);
            sorted.add(omf);
        }
        if (dynamic)
        {
            // add the jump table segment iff there were dynamic segments.
            OMF_Segment temp = new OMF_Segment();
            temp.SetSegmentName("~JumpTable");
            temp.SetSegmentNumber(segnum++);
            temp.SetKind(OMF.KIND_JUMPTABLE);
            temp.AddOpcode(fJumpTable);
            sorted.add(temp);
            
            for (int i = 0; i < fSegments.size(); i++)
            {
                OMF_Segment omf = fSegments.get(i).omf;
                
                if ((omf.Attributes() & OMF.KIND_DYNAMIC) == OMF.KIND_DYNAMIC)
                {
 
                    omf.SetSegmentNumber(segnum++);
                    sorted.add(omf);
                }
            }           
        }
        
        return sorted;
    }
    
    /*
     * Resolve the expressions.
     */
    protected void Resolve()
    {      
        SymbolMath o;
        OMF_Segment omf;
        
        for ( Iterator<Symbol> iter = fExpr.iterator(); iter.hasNext(); )
        {
            Symbol sym = iter.next();
            // ok, now for some fun...
            // iter.remove();
            try
            {
                o = sym.Reduce();
                omf = sym.segment;

                OMF_Relocation reloc = o.Reloc(sym.segment, sym.size, sym.location);
                int value = o.value + fOrg;
                               
                // for disasm & binary files, calculate the shift.
                if (reloc != null)
                {
                    int shift = reloc.Shift();
                    if (shift != 0)
                    {
                        if (shift > 0)
                            value = value << shift;
                        else
                            value = value >> (-shift);
                    }
                }
                
                value = Truncate(value, sym.size);

                // type is one of the OMF_*EXPR types.
                switch (sym.type)
                {
                case OMF.OMF_RELEXPR:
                    /*
                     * relative branch. truncate the number to the size, no
                     * range errors allowed.
                     */

                    // can't relocate if in different segment, or if shifted.
                    if (reloc != null)
                    {
                        if (reloc.IsInterseg() || reloc.Shift() != 0)
                        {
                            throw new LinkError(LinkError.E_RELEXPR, sym);
                        }
                    }
                    value = o.value - sym.location - sym.displacement;

                    boolean err = false;
                    // check if within range.
                    switch (sym.size)
                    {
                    case 1:
                        if (value > Byte.MAX_VALUE || value < Byte.MIN_VALUE)
                            err = true;
                        break;
                    case 2:
                        if (value > Short.MAX_VALUE || value < Short.MIN_VALUE)
                            err = true;
                        break;
                    default:
                        err = true;
                    }
                    if (err)
                        throw new LinkError(LinkError.E_RELEXPR, sym);
                    value = Truncate(value, sym.size);
                    reloc = null;

                    break;
                case OMF.OMF_LEXPR:
                    /*
                     * create a jump table entry if needed.
                     * 1) single label
                     * 2) fixed, constant offset
                     * 3) in another segment
                     * 4) other segment is dynamic code segmeny
                     * -- should be preceeded by 0x22 JSL
                     */
                    if (reloc != null && reloc.IsInterseg()
                            && reloc.Shift() == 0)
                    {
                        if ((((SymbolLabel) o).segment.Attributes() & OMF.KIND_DYNAMIC) != 0)
                        {
                            // int16 userid (0x0000)
                            // int16 file
                            // int16 segment number
                            // int32 offset
                            // int32 jsl (0x22000000)
                            OMF_Interseg inter = (OMF_Interseg) reloc;
                            fJumpTable.AppendInt16(0, 0);
                            fJumpTable.AppendInt16(inter.File(), 0);
                            fJumpTable.AppendInt16(inter.Segment(), 0);
                            fJumpTable.AppendInt32(value, 0);
                            fJumpTable.AppendInt32(0x22000000, 1);
                        }
                    }
                    break;
                case OMF.OMF_BKEXPR:
                    /*
                     * truncated bits must match current location counter.
                     * 
                     * therefore:
                     *  relocation limits:
                     *   same segment
                     *   no shift
                     *  limits:
                     *    truncated bits must match.
                     *    ie - mask off and compare.
                     *   
                     */
                    if (reloc != null)
                    {
                        if (reloc.IsInterseg() || reloc.Shift() != 0)
                            throw new LinkError(LinkError.E_BKEXPR, sym);  
                    }
                    int a = o.value;
                    int b = sym.location;
                    
                    switch (sym.size)
                    {
                    case 1:
                        a = a & 0xffffff00;
                        b = b & 0xffffff00;
                        break;
                    case 2: 
                        a = a & 0xffff0000;
                        b = b & 0xffff0000;
                        break;
                    case 3:
                        a = a & 0xff000000;
                        b = b & 0xff000000;
                        break;                    
                    }
                    if (a != b)
                    {
                        throw new LinkError(LinkError.E_BKEXPR, sym);   
                    }
                    
                    //System.out.printf("BK Expr: %1$06x %2$06x\n",
                    //        value, sym.location);
                    
                    break;
                    
                    

                case OMF.OMF_ZPEXPR:
                    /*
                     * truncated bits must be 0. Relocation is NOT allowed.
                     */
                    if (reloc != null)
                    {
                        throw new LinkError(LinkError.E_ZPEXPR, sym);
                    }
                    if (value != o.value)
                    {
                        throw new LinkError(LinkError.E_ZPEXPR, sym);
                    }
                    break;

                case OMF.OMF_EXPR:
                    /*
                     * anything goes.
                     */

                    break;

                }

                if (reloc != null)
                {
                    omf.AddOpcode(reloc.Compress());
                }
                // as a convenience for disasm/debugging, store the value
                // even if it's a relocation record.
                sym.data.Modify(sym.location, value, sym.size, omf.NumberSex());
                

            } catch (LinkError e)
            {
                System.out.println(e.toString());
            }
        }
        
        /* if this is an RTL file, we must go through the global symbol table and add
         * an entry for every function and/or equate.
         */
        if ((fFlags & FLAG_RTL) != 0)
        {
            Iterator<Entry<String, Symbol>> iter = fGlobal.Iterator();
            for (; iter.hasNext(); )
            {
                Entry<String, Symbol> entry = iter.next();
                String name = entry.getKey();
                Symbol sym = entry.getValue();
                int segnum;
                int value;
                omf = sym.segment;
                
                try
                {
                    o = sym.Reduce();
                    value = o.Value();
                    
                    if (o instanceof SymbolLabel)
                    {
                        segnum = omf.SegmentNumber();
                        if (o.Shift() != 0)
                        {
                            throw new LinkError(LinkError.E_COMPLEX, sym);
                        }
                    }
                    else
                    {
                        segnum = 0;
                        
                    }
                    
                    fExtra.AddOpcode(new OMF_Entry(segnum, value, name));
                }
                catch (LinkError e)
                {
                    System.out.println(e.toString());
                }
                // 
                
                
            }
            
        }

    }

    /*
     * truncate a number to size bytes.
     */
    private static int Truncate(int number, int size)
    {
        // size will be 1, 2, 3, 4... not likely to be more.
        switch (size)
        {
        case 0:
            return 0;
        case 1:
            return number & 0xff;
        case 2:
            return number & 0xffff;
        case 3:
            return number & 0xffffff;
        case 4:
            return number;
        }
        return number;
    }

    @SuppressWarnings("unchecked")
    public void ProcessLibrary(File f)
    {
        /* TODO -- known problems:
         * missing equ/gequ references are ignored.
         * could have ProcessSegment store them in the expression array
         * and have Resolve ignore them.
        
         * libraries cannot refer to previous libraries.  
         * Orca/Linker, ld, etc have the same limitations.  
         * this could be done, but it would require 
         * a local crossmap for each file as well as a global crossmap.
         * 
         * eg: {HashMap segments, HashSet processed}
         * + global segments, global processed.
         */

        ArrayList<OMF_Segment> segments = OMF.LoadFile(f);

        if (segments == null)
        {
            System.out.print(f.getName() + " is not a valid OMF file.");
            return;
        }

        HashMap<String, OMF_Segment> crossmap = new HashMap<String, OMF_Segment>();
        HashSet<OMF_Segment> processed = new HashSet<OMF_Segment>();

        // copy the segments into a hash array.
        for (Iterator<OMF_Segment> iter = segments.iterator(); iter.hasNext();)
        {
            OMF_Segment omf = iter.next();
            if (omf.Kind() == OMF.KIND_LIBDICT)
                continue;

            crossmap.put(omf.SegmentName(), omf);
            
            // we also have to scan the file for GLOBAL entries ... maybe also GEQUs.
            for(Iterator<OMF_Opcode> opIter = omf.Opcodes(); opIter.hasNext(); )
            {
                OMF_Opcode opcode = opIter.next();
                switch(opcode.Opcode())
                {
                case OMF.OMF_GLOBAL:
                case OMF.OMF_GEQU:                   
                    crossmap.put(opcode.toString(), omf);
                    break;
                }
            }
        }
        
        // no longer needed, so be nice.
        segments.clear();
        segments = null;

        SymbolTable local = new SymbolTable();
        HashSet<String> using = new HashSet<String>();
        ArrayList<Symbol> expr = new ArrayList<Symbol>();
        HashSet<String> missing = FindMissing(fExpr);
        if (missing.size() == 0)
            return;

        
        /*
         * the first time, this will search the global missing list, afterwards,
         * if there were any changes, the new missing list will be used. Then,
         * we include any using segments ... continue until done.
         */
        for (boolean delta = false; ; delta = false)
        {
 
            if (crossmap.size() == 0) break;
            // add all missing segments.... which could require more...
            for (Iterator<String> iter = missing.iterator(); iter.hasNext();)
            {
                String name = iter.next();

                OMF_Segment omf = crossmap.get(name);
                if (omf == null)
                    continue;
                if (processed.contains(omf))
                {
                    continue;
                }
                
                // we have it ... hooray!!!
                this.ProcessSegment(omf, local, using, expr);
                processed.add(omf);
                //iter.remove();
                //crossmap.remove(omf.SegmentName());
                delta = true;
            }
            // add in any using segments...
            // we need to clone it since we will modify the using set....
            HashSet<String> xusing = (HashSet<String>) using.clone();
            for (Iterator<String> iter = xusing.iterator(); iter.hasNext();)
            {
                String name = iter.next();

                OMF_Segment omf = crossmap.get(name);
                if (omf == null)
                    continue;
                if (omf.Kind() != OMF.KIND_DATA)
                    continue; // oops.
                // we have it ... hooray!!!
                this.ProcessSegment(omf, local, using, expr);

                using.remove(name);
                crossmap.remove(name);
                delta = true;
            }

            if (delta)
            {
                // create a new list based on ONLY the new expressions.
                missing = FindMissing(expr);
            }
            else break;
        }
        // add in all the new expressions.
        fExpr.addAll(expr);

    }

    /*
     * find all undefined symbols.
     */
    private HashSet<String> FindMissing(ArrayList<Symbol> list)
    {
        HashSet<String> missing = new HashSet<String>();
        // TODO - another array for equ/gequ symbols??

        for (Iterator<Symbol> iter = list.iterator(); iter.hasNext();)
        {
            Symbol sym = iter.next();
            sym.FindMissing(missing);
        }
        return missing;
    }

    /*
     * link in an rtl file.
     * segment is a library segment type, should contain 1 ENTRY record.
     * 
     * returns true if this was an rtl, false otherwise.
     */
    private boolean ProcessRTL(OMF_Segment segment, String name)
    {
        if (segment.Kind() != OMF.KIND_LIBDICT) return false;
        HashSet<String> missing = FindMissing(fExpr);
        boolean found = false;
        int fileno = fFileno + 1;

        int segcount = 0;
        // 1 -- scan through and find how many segments there are.
        // 2 -- build an array of segments
        // 3 -- (re-) scan and add an entry.
        
        for(Iterator<OMF_Opcode> opIter = segment.Opcodes(); opIter.hasNext(); )
        {
            OMF_Opcode op = opIter.next();
            if (!(op instanceof OMF_Entry)) return false;
            OMF_Entry entry = (OMF_Entry)op;
            int tmp = entry.Segment();
            if (tmp > segcount) segcount = tmp;
        }
        // 1-indexed, not 0 indexed.
        segcount++;
        OMF_Segment segments[] = new OMF_Segment[segcount];
        for (int i = 0; i < segcount; i++)
        {
            OMF_Segment tmp = new OMF_Segment();
            tmp.SetFile(fileno);
            tmp.SetSegmentNumber(i);
            segments[i] = tmp;
        }
        
        for(Iterator<OMF_Opcode> opIter = segment.Opcodes(); opIter.hasNext(); )
        {
            OMF_Opcode op = opIter.next();
            OMF_Entry entry = (OMF_Entry)op;
            if (missing.contains(entry.toString()))
            {                
                Symbol sym = new Symbol(null, null);
                sym.segment = segments[entry.Segment()];
                sym.location = entry.Offset();
                sym.offset = 0;
                sym.segmentname = "";
                sym.size = 0;
                sym.type = 'I';
                found = true;
                
                // create the pathname segment....
            }
        }
        if (found)
        {
            fFileno++;
            // add an entry to the pathname...
            fPathName.AppendInt16(fFileno, 0);
            // TODO -- modification date
            fPathName.AppendData(8, (byte)0);
            // TODO -- support for full pathname.
            int l = name.length();
            fPathName.AppendData((byte)l);
            for (int i = 0; i < l; i++)
            {
                fPathName.AppendData((byte)name.charAt(i));
            }
            
            
        }
        
        return true;
    }
    
    
    /**
     * 
     * @param io
     *            the OMF file to read from.
     * @param lib
     *            true if this is a library, false otherwise. For libraries,
     *            segments will only be linked if there is an unresolved
     *            reference to it.
     */
    private void Process(FileInputStream io, boolean lib)
    {

    }

    private void ProcessSegment(OMF_Segment o, SymbolTable local,
            HashSet<String> __using, ArrayList<Symbol> __expr)
    {
        boolean isPrivate;
        boolean isData;
        String loadname;
        String segname;
        Symbol sym;

        OMF_Opcode op;
        int opcode;
        SegmentData seg = null;

        ArrayList<String> using = new ArrayList<String>();

        // all segments within a loadname will be packed into
        // 1 segment with that as the segment name.
        loadname = o.LoadName();
        segname = o.SegmentName();
        isPrivate = o.Private();
        isData = o.Kind() == OMF.KIND_DATA;

        // get the appropriate segment...
        seg = fSegments.get(loadname);
        if (seg == null)
        {
            seg = new SegmentData();
            seg.omf.SetSegmentName(loadname);
            int i = fSegments.add(seg, loadname);
            seg.omf.SetSegmentNumber(i + 1);
            
        }
        
        
        OMF_Data data = seg.data;
        OMF_Segment omf = seg.omf;
        
        seg.SetAttributes(o.Attributes());
        seg.SetKind(o.Kind());

        // now create a local/global symbol for this segment name.

        // show the module name, segment name, and location.
        if ((fFlags & FLAG_MAP) != 0)
        {
            System.out.printf("%1$-30s: %2$04x\n", segname, data.CodeSize());
        }
        int startpc = data.CodeSize();

        sym = new Symbol(null, null);
        sym.segment = omf;
        sym.location = startpc;
        sym.offset = 0;
        sym.segmentname = segname;
        sym.size = 0;
        sym.type = 'I';

        if (isPrivate)
        {
            local.Insert(segname, sym, null);
        } else
            fGlobal.Insert(segname, sym, null);

        Iterator<OMF_Opcode> iter = o.Opcodes();

        boolean eof = false;
        while (iter.hasNext())
        {
            op = iter.next();
            opcode = op.Opcode();

            if (opcode == OMF.OMF_EOF)
            {
                eof = true;
                break;    
            }
                

            switch (opcode)
            {
            case OMF.OMF_LCONST:
                /*
                 * LConst may actually be a Const, but both are the same class.
                 */
                data.AppendData(op);
                break;
                
            case OMF.OMF_DS:
                {
                    data.AppendData(op);
                }
                break;

            case OMF.OMF_EXPR:
            case OMF.OMF_ZPEXPR:
            case OMF.OMF_BKEXPR:
            case OMF.OMF_LEXPR:
                // an expression. Store blanks in the data for now,
                // update the expression list and unresolved label list.
                {
                    int size = op.CodeSize();
                    int pc = data.CodeSize();

                    sym = new Symbol(local, fGlobal);
                    sym.segment = omf;
                    sym.location = pc;
                    sym.offset = pc - startpc;
                    sym.segmentname = segname;
                    sym.size = size;
                    sym.expression = ((OMF_Expr) op).Expression();
                    sym.using = using;
                    sym.type = opcode;
                    sym.data = data;
                    __expr.add(sym);

                    // ok, insert the expression into the local
                    // expression
                    // list, along with the location, segment, and using
                    // list.
                    // at the end of this file, all private labels will
                    // be
                    // reduced and everything else will go into the
                    // global
                    // expression list.

                    data.AppendData(op);

                }
                break;
            case OMF.OMF_RELEXPR:
                // a relative expression...
                {
                    int size = op.CodeSize();
                    int pc = data.CodeSize();

                    sym = new Symbol(local, fGlobal);
                    sym.segment = omf;
                    sym.location = pc;
                    sym.offset = pc - startpc;
                    sym.segmentname = segname;
                    sym.size = size;
                    sym.expression = ((OMF_RelExpr) op).Expression();
                    sym.using = using;
                    sym.type = opcode;
                    sym.displacement = ((OMF_RelExpr) op).Displacement();
                    sym.data = data;
                    __expr.add(sym);

                    data.AppendData(op);

                }
                break;


                
                
            case OMF.OMF_USING:
                using.add(op.toString());
                __using.add(op.toString());
                break;
            case OMF.OMF_STRONG:
                // strong.add(op.toString());
                break;

            /*
             * # is a boundary for alignment; fill with 0s. 0 = no alignment
             * needed 0x0100 = page align boundary
             * 
             */
            case OMF.OMF_ALIGN:
                {
                    int align = ((OMF_Align) op).Value();
                    int pc = data.CodeSize();
                    if (align != 0)
                    {
                        // TODO - align must be power of 2.
                        // find the correct boundary.
                        int newpc = (pc + align) & ~(align - 1);
                        if (newpc != pc)
                        {
                            data.AppendData(newpc - pc, (byte) 0);
                        }
                    }
                }
                break;
            /*
             * increment/decrement location counter. the orca/linker only
             * supports positive org values; supporting a negative org could
             * potentially interfere with expression/relocation records.
             */
            case OMF.OMF_ORG:
                {
                    int org = ((OMF_Org) op).Value();
                    if (org < 0)
                    {
                        // TODO - error if org < 0
                    } else if (org > 0)
                    {
                        data.AppendData(org, (byte) 0);
                    }
                }
                break;

            /*
             * LOCAL - same as $E6 except that it is a true local label, and is
             * ignored by the link editor unless the module is a data area.
             */
            case OMF.OMF_LOCAL:
                if (isData)
                {
                    OMF_Local l = (OMF_Local) op;

                    int pc = data.CodeSize();

                    sym = new Symbol(local, fGlobal);
                    sym.segment = omf;
                    sym.location = pc;
                    sym.offset = pc - startpc;
                    sym.segmentname = segname;
                    sym.size = l.Length();
                    sym.type = l.Type();

                    local.Insert(op.toString(), sym, segname);
                }
                break;

            case OMF.OMF_GLOBAL:
                {
                    OMF_Local l = (OMF_Local) op;

                    int pc = data.CodeSize();

                    if ((fFlags & FLAG_MAP) != 0)
                    {
                        System.out.printf("%1$-30s: %2$04x\n", 
                                op.toString(), pc);
                    }
                    
                    sym = new Symbol(local, fGlobal);
                    sym.segment = omf;
                    sym.location = pc;
                    sym.offset = pc - startpc;
                    sym.segmentname = segname;
                    sym.size = l.Length();
                    sym.type = l.Type();
                    if (l.Private())
                    {
                        local.Insert(op.toString(), sym, null);
                    } else
                    {
                        fGlobal.Insert(op.toString(), sym, null);
                    }

                    break;
                }
            /*
             * EQU - same as $E7, except that this is a local label, significant
             * only in data areas.
             */
            case OMF.OMF_EQU:

                if (isData)
                {
                    OMF_Equ l = (OMF_Equ) op;

                    int pc = data.CodeSize();

                  
                    
                    sym = new Symbol(local, fGlobal);
                    sym.segment = omf;
                    sym.location = pc;
                    sym.offset = pc - startpc;
                    sym.segmentname = segname;
                    sym.size = l.Length();
                    sym.type = l.Type();
                    sym.using = using;
                    sym.expression = l.Expression();

                    if (l.Private())
                    {
                        local.Insert(op.toString(), sym, segname);
                    } else
                    {
                        fGlobal.Insert(op.toString(), sym, segname);
                    }
                }
                break;

            /*
             * this goes in the local symbol table if private, global symbol
             * table if public.
             */
            case OMF.OMF_GEQU:
                {
                    OMF_Equ l = (OMF_Equ) op;

                    int pc = data.CodeSize();

                    if ((fFlags & FLAG_MAP) != 0)
                    {
                        System.out.printf("%1$-30s: %2$04x\n", 
                                op.toString(), pc);
                    }  
                    
                    sym = new Symbol(local, fGlobal);
                    sym.segment = omf;
                    sym.location = pc;
                    sym.offset = pc - startpc;
                    sym.segmentname = segname;
                    sym.size = l.Length();
                    sym.type = l.Type();
                    sym.using = using;
                    sym.expression = l.Expression();

                    if (l.Private())
                    {
                        local.Insert(op.toString(), sym, null);
                    } else
                    {
                        fGlobal.Insert(op.toString(), sym, null);
                    }

                    break;
                }

            default:
                LinkError e = new LinkError(LinkError.E_BADOP, Integer
                        .toString(opcode));
                e.SetSegment(segname);
                e.SetOffset(data.CodeSize() - startpc);
                System.out.println(e.toString());

            } // end switch
        } //end while (iter.hasNext);
        
        if (!eof)
        {
            LinkError e = new LinkError(LinkError.E_NOEOF);
            e.SetSegment(segname);
            System.out.println(e.toString());
        }
                     
        int res = o.ReservedSpace();
        if (res > 0)
        {
            data.AppendData(res, (byte)0);
        }


    }

}

class SegmentData
{
    public OMF_Data data;

    private int fKind;

    private int fAttributes;

    public OMF_Segment omf;

    public SegmentData()
    {
        fKind = -1;
        fAttributes = -1;
        data = new OMF_Data();
        omf = new OMF_Segment();
        omf.AddOpcode(data); // TODO -- always do this?
    }

    public void SetAttributes(int attr)
    {
        attr &= (~OMF.KIND_PRIVATE);
        
        if (fAttributes == -1)
        {
            fAttributes = attr;
            omf.SetAttributes(fAttributes);
        }
        // upgrade the attributes ???
        else
        {

        }
    }

    public boolean SetKind(int kind)
    {
        if (fKind == -1)
        {
            fKind = kind;
            omf.SetKind(fKind);
            return true;
        } else
        {
            // data may be upgraded to code, init, or dp.
            // data and code are eqivalent.
            
            if (kind == OMF.KIND_DATA)
            {
                if (fKind == OMF.KIND_DATA || fKind == OMF.KIND_INIT || fKind == OMF.KIND_DP)
                {
                    return true;
                }
            }
            if (fKind == OMF.KIND_DATA)
            {
                if (kind == OMF.KIND_DATA || kind == OMF.KIND_INIT || kind == OMF.KIND_DP)
                {
                    fKind = kind;
                    omf.SetKind(kind);
                    return true;
                }               
            }

        }
        return false;
    }
}
