

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;

import omf.OMF;
import omf.OMF_Segment;


public class lseg
{
    static private final String[] SegKinds = 
    {
        "Code",
        "Data",
        "Jump-table",
        "",
        "Pathname",
        "",
        "",
        "",
        "Library Dictionary",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "Initialization",
        "",
        "Direct-page/Stack"
    };        
        
    public lseg() {
    }
    /**
     * @param args
     */
    public static void main(String[] args)
    {

        ArrayList<OMF_Segment> segments;
        

        System.out.println("File                 Type               Size     Name");
        System.out.println("-------------------- ------------------ -------- ----------------");
        for (int i = 0; i < args.length; i++)
        {
            File f = new File(args[i]);
            if (!f.exists())
            {
                System.out.println("No such file: " + args[i]);
                continue;
            }
            segments = OMF.LoadFile(f);
            if (segments == null)
            {
                System.out.println("Invalid OMF file: " + args[i]);
                continue;
            }
            for (Iterator<OMF_Segment> iter = segments.iterator(); iter.hasNext(); )
            {
                OMF_Segment segment = iter.next();               
                int kind = segment.Kind();
                
                String Kind;
                if (kind > SegKinds.length)
                    Kind = "";
                else
                    Kind = SegKinds[kind];
                
                System.out.printf("%1$-20s %2$-18s 0x%3$06x \"%4$s\"\n",
                            f.getName(),
                            Kind, 
                            segment.Length(),
                            segment.SegmentName()
                            );
            }
        }

    }

}
