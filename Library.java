/*
 * Created on Nov 22, 2006
 * Nov 22, 2006 3:52:36 AM
 */
/*
 * 
 * This class deals with the Library segments. 
 * 
 * For library files, the first OMF Segment MUST be
 * a of type LIBDICT (0x08).  Orca Makelib gives it a
 * name of "LIBRARY", but that's not necessary.
 * 
 * The LIBRARY segment contains 3 LCONST records, each 
 * of which is a repeating record (1 & 3 are variable length)
 * 
 * 1) filename information. Format is :
 * [
 *  file_number (16 bit)
 *  file_name (pstring)
 * ]
 * 
 * 2) offset information.  Format is :
 * [
 *   name_offset (32 bit, offset into record 3)
 *   file_number (16 bit, matches record 1)
 *   private_flag (16 bit)
 *   data_offset (32 bit, absolute offset into the file to the start
 *      of this segment.)
 * ]
 * 
 * 3) symbol name information.  Format is :
 * [
 *  pstring symbol_name 
 * ]
 * 
 * strings are always pstrings (ie, LABLEN will always be ignored)
 * 
 * each file added to a library is given a non-0 number which is used
 * to keep track of the symbol/segments and the file they originally came
 * from.  record 3 is sorted alphabetically, which may or may not be 
 * of significance.
 * 
 */
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;

import omf.*;


/*
 * TODO -- due to entry records, etc, 1 OMF segment may be 
 * referenced under multiple names... we need a nie method to only
 * load each segment once... ie store file position (offset), read
 * a segment, then map it to the appropriate symbols.
 */

public class Library
{
    Library()
    {
        FileList = new ArrayList<FileRec>();
        FileList.add(0, null); // prevent 0 from being used.
        SymbolList = new ArrayList<SymbolRec>();
        fDirty = false;
    }

    Library(File f)
    {
        this();
        
        if (!f.exists()) return;
        
        try
        {
            FileInputStream io = new FileInputStream(f);
            byte[] buffer;
            buffer = new byte[io.available()];
            io.read(buffer);
            ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
            
            OMF_Segment omf = new OMF_Segment(bais);
            // TODO -- verify if mark/reset work or not.
            if (omf.Kind() == OMF.KIND_LIBDICT)
                LoadDictionary(bais, omf);
            
            
        }
        catch(Exception e)
        {
           e.printStackTrace();
        }
        
    }
    
    private boolean LoadDictionary(InputStream io, OMF_Segment omf)
    {
        ListIterator<OMF_Opcode >iter = omf.Opcodes();

        int i = 0;
        boolean error = false;
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
                        int num = read16(data, j);
                        j += 2;
                        int len = read8(data, j);
                        j++;
                        String s = new String(data, j, len);
                        j += len;
                        
                        // may be out of order.
                        for (int k = FileList.size(); k <= num; k++)
                        {
                            FileList.add(k, null);
                        }
                        FileRec f = new FileRec(s);
                        f.FileNumber = num;
                        FileList.set(num, f);
                    }
                    break;
                    
                case 1:
                    offset_data = data;
                    break;
                    
                case 2:
                    // now we can processe the offset_data from above.
                    while (j < offset_data.length)
                    {
                        SymbolRec s = new SymbolRec();
                        
                        int offset = read32(offset_data, j);
                        j += 4;
                        int k = read8(data, offset);

                        s.SymbolName = new String(data, offset + 1, k);
                        
                        s.FileNumber = read16(offset_data, j);
                        j += 2;
                                             
                        FileRec f = FileList.get(s.FileNumber);
                        s.FileName = f.FileName;
                        
                        s.Private = read16(offset_data, j) != 0;
                        j += 2;
                        
                        s.Offset = read32(offset_data, j);
                        j += 4;
                        
                        f.Symbols.add(s);
                        SymbolList.add(s);
                        
                    }
                    break;
                    
                    
                default:
                    error = true;
                
                }
            }
            else error = true;
        }    
    
        // now load the actual segments.

        for (SymbolRec s : SymbolList)
        {
            try 
            {
                io.reset();
                io.skip(s.Offset);
                s.Segment = new OMF_Segment(io);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return false;
            }
            if (s.Segment == null) 
                return false;
        }

        return !error;
    }
    
    SymbolRec FindSymbol(String name)
    {
        return FindSymbol(name, 0);
    }
    
    SymbolRec FindSymbol(String name, int fnum)
    {
        Iterator<SymbolRec> iter;
        if (fnum != 0)
        {
            FileRec f = FileList.get(fnum);
            iter = f.Symbols.iterator();
        }
        else
        {
            iter = SymbolList.iterator();
        }
        for (; iter.hasNext(); )
        {
            SymbolRec s = iter.next();
            
            if (s.SymbolName != name) continue;
            
            if (s.Private && (s.FileNumber != fnum))
                return null;
            
            return s;
            
        }
        
        return null;
    }
    
 
    int NewFile(String filename)
    {
       FileRec r = new FileRec(filename);
       
       FileList.add(r);
       r.FileNumber = FileList.size() - 1;
       
       return r.FileNumber;
    }
 
    
    private boolean AddSymbol(FileRec f, OMF_Segment segment)
    {
        // TODO -- check if name is unique?
        
        SymbolRec s = new SymbolRec();
        s.SymbolName = segment.SegmentName();
        s.FileNumber = f.FileNumber;
        s.FileName = f.FileName;
        s.Private = segment.Private();
        s.Offset = 0; // TODO -???
        s.Segment = segment;
        
        f.Symbols.add(s);
        SymbolList.add(s);
        
        fDirty = true;
        
        return true;         
    }    
    
    boolean AddSymbol(String filename, OMF_Segment segment)
    {
        
        FileRec f = FindFileRec(filename);
        if (f == null)
        {
            f = new FileRec(filename);
            FileList.add(f);
            f.FileNumber = FileList.size() - 1;           
        }
        return AddSymbol(f, segment);
    }

    
    boolean AddSymbol(int fnum, OMF_Segment segment)
    {
        FileRec f = FileList.get(fnum);
        if (f == null) return false; 
        return AddSymbol(f, segment);
    }
    
    /*
     * remove the file.... 
     */
    boolean RemoveFile(String filename)
    {
        FileRec f = FindFileRec(filename);
        if (f == null) return false;
        int num = f.FileNumber;
       
        FileList.set(f.FileNumber, null);
        for (int i = SymbolList.size(); i > 0; i--)
        {
            SymbolRec s = SymbolList.get(i - 1);
            if (s.FileNumber == num)
            {
                FileList.remove(i - 1);
            }
        }
        
        fDirty = true;
        return true;
    }
    
    /*
     * extract the file ... does not remove from the library, 
     */
    boolean ExtractFile(String filename, FileOutputStream out)
    {
        FileRec f = FindFileRec(filename);
        if (f == null) return false;
        
        
        for (SymbolRec s : f.Symbols)
        {
            if (!s.Segment.Save(out))
                return false;
        }
        
        return true;
    }
    
    boolean AddFile(String filename, File in)
    {
        
        return true;
    }
    
    
    void Save(File Outfile)
    {
        Optimize();
        /*
         * TODO -- sort the filelist alphabetically, create header
         * segment with 3 LCONST records, then save each segment.
         * after saving, overwrite the const records with the offset
         * values.
         * 
         * Alternate approach:
         * write the segments to a temporary file, keeping track of
         * segment lengths for offset information.  Then write the 
         * Library header to a new file and merge the temp. file to it.
         */
    }
    
    /*
     * in case of deletion, renumber any files.
     */
    private void Optimize()
    {
        boolean delta = false;
        
        for (int i = FileList.size() - 1; i > 0; i--)
        {
            FileRec f = FileList.get(i);
            if (f == null)
            {
                FileList.remove(i);
                delta = true;
            }
        }
        if (delta)
        {
            for (int i = FileList.size() - 1; i > 0; i--)
            {
                FileRec f = FileList.get(i);
                f.FileNumber = i;
                
                // renumber symbols as well.
                for (SymbolRec s : f.Symbols)
                {
                    s.FileNumber = i;
                }
            }
        }
        
    }

    
    private FileRec FindFileRec(String name)
    {
        if (name == null || name.length() == 0) return null;
        
        for (FileRec f : FileList)
        {
            if (f == null) continue;
            if (f.FileName.equals(name)) return f;
        }
        
        return null;
    }
    
    // I hate the lack of an unsigned type.
    private static int read32(byte[] bytes, int offset)
    {
        int x1 = bytes[offset++] & 0x00ff;
        int x2 = bytes[offset++] & 0x00ff;
        int x3 = bytes[offset++] & 0x00ff;
        int x4 = bytes[offset++] & 0x00ff;
        return x1 + (x2 << 8) + (x3 << 16) + (x4 << 24);
    }    
    private static int read16(byte[] bytes, int offset)
    {
        int x1 = bytes[offset++] & 0x00ff;
        int x2 = bytes[offset++] & 0x00ff;
        return x1 + (x2 << 8);
    }
    private static int read8(byte[] bytes, int offset)
    {
        return bytes[offset] & 0x00ff;
    }
    
    
    public Iterator<SymbolRec> Symbols()
    {
        return SymbolList.iterator();
    }
    public Iterator<FileRec> Files()
    {
        return FileList.iterator();
    }   
    /*
    public Iterator<SymbolRec> iterator_()
    {

        class SRIterator implements Iterator<SymbolRec>
        {
            ArrayList<FileRec> fList;
            private int fIndex;
            private Iterator<SymbolRec> fIter;
            private boolean fEof;
            
            public SRIterator(ArrayList<FileRec> files)
            {
                fList = files;
                fIndex = 0;
                fIter = null;
                fEof = false;
            }
            
            public boolean hasNext()
            {
                if (fEof) return true;
                
                for (;;)
                {
                    if (fIter == null)
                    {
                        fIndex++;
                        if (fIndex >= fList.size())
                        {
                            fEof = true;
                            return false;
                        }
                        FileRec f = fList.get(fIndex);
                        if (f == null) continue;
                        if (f.SymolList.size() == 0) continue;
                        
                        fIter = f.SymbolList.iterator();
                    }
                    if (fIter.hasNext()) return true;
                }
            }

            public SymbolRec next()
            {
                if (!hasNext()) return null;

                return fIter.next();
            }

            public void remove()
            {
                if (fIter != null)
                    fIter.remove();
                
            }
            
        }
        
        return new SRIterator(FileList);
    }
    */
    
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
    
    
    private ArrayList<FileRec> FileList;
    private ArrayList<SymbolRec> SymbolList;
    private boolean fDirty;

    
}
