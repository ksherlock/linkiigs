/*
 * Created on Dec 15, 2006
 * Dec 15, 2006 7:42:23 PM
 */


// TODO -- actually use fPublicSymbols.

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.TreeSet;

import omf.*;
import omf.io.OMF_DataOutput;
import omf.io.__OMF_Writer;


public class OMF_Library
{
    public static final int E_OK = 0;
    public static final int E_NOFILE = -1; // file does not exist
    public static final int E_NOTOMF = -2; // not an OMF file
    public static final int E_NOTOBJECT = -3; // not an object OMF file
    public static final int E_DUPLICATE_FILE = -4; // duplicate file name.
    public static final int E_DUPLICATE_SYMBOL = -5; // duplicate symbol name
    
    public OMF_Library()
    {
        fDirty = false;
        //fHeader = new OMF_Segment();
        fSymbolList = new ArrayList<SymbolRec>();
        fFileList = new ArrayList<FileRec>();
        //fSegments = new ArrayList<OMF_Segment>();
        
        fPublicSymbols = new HashMap<String, SymbolRec>();
        
        fFileList.add(0, null); // prevent 0 from being used.
    }
    
    //
    public Iterator<SymbolRec> Symbols()
    {
        return fSymbolList.iterator();
    }
    public Iterator<FileRec> Files()
    {
        return fFileList.iterator();
    }  
    
    
    
    
    public static OMF_Library LoadFile(File file)
    {
        
        OMF_Library lib = new OMF_Library();
        //ArrayList<OMF_Segment> segarray = new ArrayList<OMF_Segment>();
        
        OMF_Segment header;
        try
        {
            RandomAccessFile rfile = new RandomAccessFile(file, "r");
            
            //lib.fHeader = new OMF_Segment(rfile);
            header = new OMF_Segment(rfile);
            if (header.Error()) return null;
            if(header.Kind() != OMF.KIND_LIBDICT) return null;
            
            // verify it's a library header.
            ListIterator<OMF_Opcode >iter = header.Opcodes();

            int i = 0;

            byte[] offset_data = null;
            
            for( ; iter.hasNext(); )
            {
                OMF_Opcode op = iter.next();
                if (op instanceof OMF_Eof) break;
                if (op instanceof OMF_Const)
                {
                    byte[] data = ((OMF_Const)op).Data();
                    int j = 0;
                    
                    switch (i++)
                    {
                    // files.
                    case 0:
                        while (j < data.length)
                        {
                            int num = OMF.Read16(data, j, 0);
                            j += 2;
                            int len = OMF.Read8(data, j, 0);
                            j++;
                            String s = new String(data, j, len);
                            j += len;
                            
                            // may be out of order.
                            for (int k = lib.fFileList.size(); k <= num; k++)
                            {
                                lib.fFileList.add(k, null);
                            }
                            FileRec f = lib.new FileRec(s);
                            f.FileNumber = num;
                            lib.fFileList.set(num, f);
                        }
                        break;
                        
                    case 1:
                        offset_data = data;
                        break;
                        
                    case 2:
                        // now we can processe the offset_data from above.
                        while (j < offset_data.length)
                        {
                            SymbolRec s = lib.new SymbolRec();
                            
                            int offset = OMF.Read32(offset_data, j, 0);
                            j += 4;
                            int k = OMF.Read8(data, offset, 0);

                            s.SymbolName = new String(data, offset + 1, k);
                            
                            s.FileNumber = OMF.Read16(offset_data, j, 0);
                            j += 2;
                                                 
                            FileRec f = lib.fFileList.get(s.FileNumber);
                            s.FileName = f.FileName;
                            
                            s.Private = OMF.Read16(offset_data, j, 0) != 0;
                            j += 2;
                            
                            s.Offset = OMF.Read32(offset_data, j, 0);
                            j += 4;
                            
                            f.Symbols.add(s);
                            lib.fSymbolList.add(s);
                            
                        }
                        break;
                        
                        
                    default:
                        return null; // error.
                    
                    }
                }
                else return null;
            }   // for( ;iter.hasNext(); )          
            
            // now load all the segments and map them (via offset) to the library.
            
            // due to global and gequ, an omf segment may correspond to multiple
            // symbol names.
            
            long offset = rfile.getFilePointer();
            for (; offset < rfile.length();)
            {
                OMF_Segment seg = new OMF_Segment(rfile);
                if (seg.Error()) return null;
                
                // find symbols that match this segment...
                // TODO -- verify FileNumber for all of them match?
                for (SymbolRec sr : lib.fSymbolList)
                {
                    if (sr.Offset == offset)
                    {
                        sr.Segment = seg;
                    }
                }
                
                offset = rfile.getFilePointer();
            }
            
            
            
        }
        catch (Exception e)
        {
            return null;
        }

        return lib;
    }
    

    
    public boolean IsDirty()
    {
        return fDirty;
    }
    
    
    /*
     * add a file to the library.  returns E_OK on success, something else
     * if there was an error.
     * 
     */
    public int AddFile(String filename, ArrayList<String> out)
    {
        out.clear();
        
        if (filename == null) return E_NOFILE;
        File f = new File(filename);
        filename = f.getName();
        if (!f.exists()) return E_NOFILE;
        if (!f.isFile()) return E_NOTOMF;
        
        if (FindFileRec(filename) != null)
            return E_DUPLICATE_FILE;
        
        ArrayList<OMF_Segment>segs = OMF.LoadFile(f);
        if (segs == null)
            return E_NOTOMF;
        
        FileRec fr = new FileRec(filename);
        ArrayList<SymbolRec> symbols = fr.Symbols;
        
        // go through the libraries
        for (OMF_Segment seg : segs)
        {
            seg.SetSegmentNumber(0);
            
            int kind = seg.Kind();
            switch(kind)
            {
            case OMF.KIND_JUMPTABLE:
            case OMF.KIND_LIBDICT:
                return E_NOTOBJECT;
            }
            // TODO -- ? check for expressload?

            ListIterator<OMF_Opcode> ops = seg.Opcodes();
            
            boolean isPrivate = seg.Private();

            if (!isPrivate && fPublicSymbols.containsKey(seg.SegmentName()))
            {
                if (out != null)
                {
                    out.clear();
                    out.add(seg.SegmentName());
                }
                return E_DUPLICATE_SYMBOL;
            }
            
            SymbolRec s = new SymbolRec();
            s.Private = isPrivate;
            s.Segment = seg;
            s.SymbolName = seg.SegmentName();
            symbols.add(s);
            if (out != null) out.add(seg.SegmentName());
            s = null;
            
            for (; ops.hasNext(); )
            {
                
                OMF_Opcode op= ops.next();
                switch(op.Opcode())
                {
                case OMF.OMF_SUPER:
                case OMF.OMF_RELOC:
                case OMF.OMF_CRELOC:
                case OMF.OMF_INTERSEG:
                case OMF.OMF_CINTERSEG:
                case OMF.OMF_ENTRY: 
                case 0xf8:  // reserved ops.
                case 0xf9:
                case 0xfa:
                case 0xfb:
                case 0xfc:
                case 0xfd:
                case 0xfe:
                case 0xff:
                case 0xe9:
                case 0xea:
                    return E_NOTOBJECT;
                    
                // TODO -- verify LOCAL/EQU if in data segment.
                // TODO -- check for duplicate labels?
                    // APW makelib does not, orca does.
                case OMF.OMF_GLOBAL:
                case OMF.OMF_GEQU:
                    
                    isPrivate = ((OMF_Local)op).Private();
                    if (!isPrivate && fPublicSymbols.containsKey(op.toString()))
                    {
                        if (out != null)
                        {
                            out.clear();
                            out.add(op.toString());
                        }
                        return E_DUPLICATE_SYMBOL;
                    }                    
                    
                    s = new SymbolRec();
                    s.Segment = seg;
                    //s.Private = isPrivate;
                    s.Private = isPrivate;
                    s.SymbolName = op.toString();
                    symbols.add(s);
                    if (out != null) out.add(op.toString());
                }
            }
            
        }
        
        // add 
        int num = fFileList.size();
        fr.FileNumber = num;
        fFileList.add(fr);
        
        for (SymbolRec s : symbols)
        {
            s.FileNumber = num;
            s.FileName = filename;
            fSymbolList.add(s);
        }
        fDirty = true;
        
        return E_OK;
    }
    
    
    
    /*
     * returns false if the file was not present to begin with
     * return true otherwise.
     */
    public boolean RemoveFile(String filename)
    {
        FileRec f = FindFileRec(filename);
        return RemoveFile(f);
    }
    
    public boolean RemoveFile(int filenum)
    {
        if (filenum < 1 || filenum >= fFileList.size()) 
            return false;

        FileRec f = fFileList.get(filenum);
        return RemoveFile(f);
    }
    
    
    private boolean RemoveFile(FileRec f)
    {
        if (f == null) return false;
        int num = f.FileNumber;
        
        fFileList.set(f.FileNumber, null);
        
        Iterator<SymbolRec> iter = fSymbolList.iterator();
        for (; iter.hasNext(); )
        {
            SymbolRec s = iter.next();
            if (s.FileNumber == num)
                iter.remove();
        }
     
        fDirty = true;
        return true;
    }
    
    
    public ArrayList<OMF_Segment> GetSegments(String filename)
    {
        FileRec f = FindFileRec(filename);
        return GetSegments(f);
    }
       
    public ArrayList<OMF_Segment> GetSegments(int filenum)
    {
        if (filenum < 1 || filenum >= fFileList.size()) 
            return null;

        FileRec f = fFileList.get(filenum);
        return GetSegments(f);
    }
    
    private ArrayList<OMF_Segment> GetSegments(FileRec f)
    {
        if (f == null) return null;
 
        ArrayList<OMF_Segment> out = new ArrayList<OMF_Segment>();

        int size = f.Symbols.size();
        for (int i = 0; i < size; i++)
        {
            SymbolRec s = f.Symbols.get(i);
            out.add(s.Segment);
        }        
        
        return out;
        
    }
    
    
    public boolean Save(File f)
    {
        if (fDirty) Renumber();
        
        RandomAccessFile raf;
        try
        {
            raf = new RandomAccessFile(f, "rw");
        }
        catch (FileNotFoundException e)
        {
            return false;
        }
        
        
        
        // Create 3 LConst records:
        
        //1) filenames
        //2) symbol records
        //3 symbol names (in alphabetical order.
        
        OMF_Data filenames = new OMF_Data();
        OMF_Data symbols = new OMF_Data();
        OMF_Data symbolnames = new OMF_Data();
        __OMF_Writer header = new OMF_DataOutput(raf);
       
        
        class SCompare implements Comparator<String>
        {
            public int compare(String arg0, String arg1)
            {
                return arg0.compareTo(arg1);
            }
            
        }
        TreeSet<String> ts;
        HashMap<String, Integer> sti = new HashMap<String, Integer>();

        ts = new TreeSet<String>(new SCompare());
        
        for (SymbolRec s : fSymbolList)
        {
            ts.add(s.SymbolName);
        }
        // now they're alphabetized.  Store into 
        // the LCONST record and make a hashlist of the
        // offsets.
        for(String s: ts)
        {
            sti.put(s, symbolnames.CodeSize());
            symbolnames.AppendString(s, 0);
        }
        
        
        /*
         * create the filename and symbols LCONST records.
         */
        for (FileRec fr : fFileList)
        {
            if (fr == null) continue;

            filenames.AppendInt16(fr.FileNumber, 0);
            filenames.AppendString(fr.FileName, 0);
        }
        
        // 10 == loadname, 8 = strlen(segname) + 1
        int offset = 0x2c + 10 + 8;
        // 15 = LCONST record overhead, +1 = OMF_EOF
        int bytecount = offset 
            + filenames.CodeSize() 
            //+ symbols.CodeSize()
            + 12 * fSymbolList.size()
            + symbolnames.CodeSize() + 15 + 1;
        
        // we make our own header rather than using the OMF_Segment... for now.
        header.Write32(bytecount);                   // bytecount
        header.Write32(0);                    // resspace
        header.Write32(0);                    // length
        header.Write8(0);                     // unused
        header.Write8(0);                     // lablen
        header.Write8(4);                     // numlen
        header.Write8(2);                     // version
        header.Write32(0);                    // banksize
        header.Write16(OMF.KIND_LIBDICT);     // kind
        header.Write16(0);                    // unused
        header.Write32(0);                    // org
        header.Write32(0);                    // alignment
        header.Write8(0);                     // numsex
        header.Write8(0);                     // unused
        //ORCA uses 0 for all segnums, APW uses sequential numbers.
        header.Write16(0);                    // segment number 
        header.Write32(0);                    // entry
        header.Write16(0x2c);                 // dispname
        header.Write16(offset);        // dispdata
        header.WriteString("", 10);
        header.WriteString("LIBRARY");
        
        

        try
        {
            
            raf.seek(bytecount);
            
            HashMap<OMF_Segment, Integer>segti = new HashMap<OMF_Segment, Integer>(); 
            
            
            for (SymbolRec s : fSymbolList)
            {
                Integer nameoffset;
                Integer segoffset;
                nameoffset = sti.get(s.SymbolName);
                segoffset = segti.get(s.Segment);
                if (segoffset == null)
                {
                    segoffset = new Integer((int)raf.getFilePointer());
                    if (!s.Segment.Save(raf)) return false;    
                }
                // in theory, the above should only save each segment once.
                           
                /*
                 * 4: name offset
                 * 2: file number
                 * 2: private flag
                 * 4: segment offset 
                 */
                symbols.AppendInt32(nameoffset.intValue(), 0);
                symbols.AppendInt16(s.FileNumber, 0);
                symbols.AppendInt16(s.Private ? 1 : 0, 0);
                symbols.AppendInt32(segoffset.intValue(), 0); // placeholder.
                
            }
           
            
            raf.seek(offset);
            filenames.Save(header);
            symbols.Save(header);
            symbolnames.Save(header);
            (new OMF_Eof()).Save(header);

            
            raf.close();
        }
        catch (Exception e)
        {
            return false;
        }
        
        
        
        
        return false;
    }
    
    
    private FileRec FindFileRec(String name)
    {
        if (name == null || name.length() == 0) return null;
        
        for (FileRec f : fFileList)
        {
            if (f == null) continue;
            if (f.FileName.compareTo(name) == 0) return f;
        }
        
        return null;
    }
    
    /*
     * renumber the library files to remove any gaps.
     * 
     */
    private void Renumber()
    {
        int size = fFileList.size();
        int i;
        boolean delta;
        
        // entry 0 is always null.
        i = 1;
        delta = false;
        while (i < size)
        {
            if (fFileList.get(i) == null)
            {
                fFileList.remove(i);
                delta = true;
                size--;
            }
            else i++;
        }
        
        if (delta)
        {
            for (i = 1; i < size; i++)
            {
                FileRec fr = fFileList.get(i);
                if (i != fr.FileNumber)
                {
                    for (SymbolRec s : fr.Symbols)
                        s.FileNumber = i;
                }
            }
        }
        
    }
    
    class SymbolRec
    {
        String SymbolName;
        String FileName;
        int FileNumber;
        boolean Private;
        int Offset;
        OMF_Segment Segment;
    }
    
    
    class FileRec
    {
        String FileName;
        int FileNumber;
        ArrayList<SymbolRec> Symbols;
        
        FileRec(String name)
        {
            Symbols = new ArrayList<SymbolRec>();
            FileName = name;
        }
        
    }   
    
    private ArrayList<FileRec> fFileList;
    private ArrayList<SymbolRec> fSymbolList; 
    private HashMap<String, SymbolRec> fPublicSymbols;
    private boolean fDirty;
    //private OMF_Segment fHeader;
    //private ArrayList<OMF_Segment> fSegments;
}
