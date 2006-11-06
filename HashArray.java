import java.util.ArrayList;
import java.util.HashMap;

/*
 * Created on Feb 20, 2006
 * Feb 20, 2006 1:08:42 AM
 */
/*
 * provides fast access via key or integer index.
 */
public class HashArray<E>
{

    
    private HashMap<String, Integer> fHash;
    private ArrayList<E> fArray;
    private int fSize;
    
    public HashArray()
    {
        fHash = new HashMap<String, Integer>();
        fArray = new ArrayList<E>();
        fSize = 0;
    }

    public int add(E data, String key)
    {
        int loc;
        Integer Loc = fHash.get(key);
        if (Loc == null)
        {
            loc = fArray.size();
            Loc = new Integer(loc);
            fHash.put(key, new Integer(loc));
            fArray.add(data);
            fSize++;
        }
        else
        {
            loc = Loc.intValue();
            fArray.set(loc, data);
        }
        return loc;
    }
    public E get(int index)
    {
        return fArray.get(index);
    }
    public E get(String key)
    {
        Integer index = fHash.get(key);
        if (index != null)
        {
            return fArray.get(index.intValue());
        }
        return null;
    }
    /*
     * will still be in the array, but the data will be nulled out.
     */
    public void remove(String key)
    {
        Integer index = fHash.get(key);
        if (index != null)
        {
            fHash.remove(key);
            fArray.set(index.intValue(), null);
            fSize--;
        }
    }
    public int size()
    {
        return fSize;
    }
    
    
    
}
