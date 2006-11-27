import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ListIterator;

import omf.OMF;
import omf.OMF_Const;
import omf.OMF_Eof;
import omf.OMF_Opcode;
import omf.OMF_Segment;

/*
 * Created on Nov 21, 2006
 * Nov 21, 2006 4:11:31 PM
 * 
 * makelib
 *  manage OMF library
 * 
 * makelib [options] librarry file
 * -a objfile : add to library (creating if necessary)
 * -d objfile : delete from library
 * -x objfile : extract from library 
 * -t : list files (short listing)
 * -v : list files (long listing)
 * -i : integrity check
 */

public class makelib
{

    private static void usage()
    {
        System.out.println("makelib v 0.1");
        System.out.println("usage: makelib [options] library");
        System.out.println("options:");

        System.out.println("\t-a objfile     add file to library (creating library, if necessary)");
        System.out.println("\t-d objfile     delete file from library");
        System.out.println("\t-x objfile     extract file from library");
        System.out.println("\t-i             test library integrity");
        System.out.println("\t-f             list library files");
        System.out.println("\t-t             list library contents");
        System.out.println("\t-v             be verbose");
        System.out.println("\t-h             help");        
    }
    
    private static void ListLib(File file, boolean verbose)
    {
        
        Library lib;
        lib = new Library(file);
       
        System.out.println("Name                           File                 Size     Private");
        System.out.println("------------------------------ -------------------- -------- -------");
        
        Iterator <Library.SymbolRec> iter;
        iter = lib.Symbols();
        for (; iter.hasNext(); )
        {
            Library.SymbolRec s = iter.next();
            System.out.printf("%1$-30s %2$-20s 0x%3$06x %4$c\n",
                    s.SymbolName,
                    s.FileName,
                    s.Segment.Length(),
                    s.Private ? 'x' : ' '
                    );                 
        }
    }
    private static void CheckLib(File file, boolean verbose)
    {
        
        Library lib;
        lib = new Library(file);
        ArrayList<OMF_Segment>segments =  OMF.LoadFile(file);
        
        
        /*
         * go through the library and verify the symbol names
         * and private flag are correct.
         * 
         * Also, this verifies no public symbols have the same name.
         */
        
        HashSet<String> set = new HashSet<String>();
        
        Iterator <Library.SymbolRec> iter;
        iter = lib.Symbols();
        for (; iter.hasNext(); )
        {
            Library.SymbolRec s = iter.next();
            OMF_Segment omf = s.Segment;
            if (!s.SymbolName.equals(omf.SegmentName()))
            {
                System.out.println("Symbol names do not match : "
                        + s.FileName + "::" + s.SymbolName 
                        + " " + omf.SegmentName());
            }
            if (s.Private != omf.Private())
            {
                System.out.println("Private flag does not match on "
                        + s.FileName + "::" + s.SymbolName);                
            }
            
            if (s.Private == false)
            {
               if (set.contains(s.SymbolName))           
                {
                    System.out.println("Duplicate symbol: " + s.SymbolName);
                }
               else set.add(s.SymbolName);
            }
        }
        
        
        
        
        /*
         * verify no files have the same name.
         */
        set.clear();
        Iterator<Library.FileRec> fiter = lib.Files();
        for ( ; fiter.hasNext(); )
        {
            Library.FileRec f = fiter.next();
            if (f == null) continue;
            if (set.contains(f.FileName))
            {
                System.out.println("Duplicate file: " + f.FileName);
            }
            else
                set.add(f.FileName);
        }
        
        /*
         * TODO -  Now we need to go through the OMF file 
         * and verify all segments are accounted for.
         */

    }
    
    private static boolean ExtractFile(Library lib, String filename)
    {
        Iterator<Library.FileRec> iter;
        
        iter = lib.Files();
        
        for (; iter.hasNext(); )
        {
            Library.FileRec f = iter.next();
            if (f == null) continue;
            
            if (f.FileName.compareToIgnoreCase(filename) == 0)
            {
                FileOutputStream io;
                try
                {
                    io = new FileOutputStream(filename);
                }
                catch (Exception e)
                {
                    return false;
                }
                for (Library.SymbolRec s : f.Symbols)
                {
                    OMF_Segment omf;
                    omf = s.Segment;
                    omf.Save(io);
                }
            }
            
        }
        
        return false;
    }
    
    // check for command line option errors.
    private static boolean CheckArgs(int a, int d, int x, boolean t, boolean i)
    {
        // if both are set, we have a conflict
        if (t && i) return false;
        if (a > 0 || d > 0 || x > 0)
        {
            // if either are set, we have a conflict
            if (i || t) return false;
            return true;
        }
        // i or t must be true since no a/d/x args.
        if (i == false && t == false)
            return false;
        // 10-4!
        return true;
    }
    
    
    
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        ArrayList<String> aList = new ArrayList<String>();
        ArrayList<String> dList = new ArrayList<String>();
        ArrayList<String> xList = new ArrayList<String>();
        
        boolean iFlag = false;
        boolean tFlag = false;
        boolean vFlag = false;
        
        GetOpt go = new GetOpt(args, "a:d:x:tivh");
        
        
        int c;
        while ((c = go.Next()) != -1)
        {
            switch(c)
            {
            case 'a':
                aList.add(go.Argument());
                break;
            case 'd':
                dList.add(go.Argument());
                break;
            case 'x':
                xList.add(go.Argument());
                break;
            case 'i':
                iFlag = true;
                break;
            case 't':
                tFlag = true;
                break;
            case 'v':
                vFlag = true;
                break;
            case 'h':
            case '?':
                usage();
                return;
                //break;
            }
        }
        
        //
        args = go.CommandLine();
        go = null;

        // if aList then library may be non-existant,
        // otherwise, it must exist.
        File f = new File(args[0]);
        if (!f.exists() && aList.size() == 0)
        {
            System.out.println("No such file: " + args[0]);
            return ;
        }
        
        if (args.length != 1 || !CheckArgs(aList.size(), dList.size(), xList.size(), 
                tFlag, iFlag))
        {
            usage();
            return;
        }

        if (tFlag)
        {
           ListLib(f, vFlag);
            return;
        }
        if (iFlag)
        {
            CheckLib(f, vFlag);
            return;
        }
        // TODO -- should I use +/-/^?
        
    }

}
