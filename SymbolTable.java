import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

class SymbolTable
{
    private SymbolMap fRoot;
    private HashMap<String,SymbolMap> fData;
    
    public SymbolTable()
    {
        fData = new HashMap<String,SymbolMap>();
        fRoot = new SymbolMap();
    }
    
    public boolean Insert(String name, Symbol sym, String dataseg)
    {
        if (dataseg == null)
        {
            return fRoot.AddEntry(name, sym);
        }

        
        // 1 - check if globally defined.
        if (fRoot.containsKey(name)) return false;
        
        // 2 - find the data segment map.
        // 2a - if there is no map, create a new one.
        SymbolMap map = fData.get(dataseg);
        if (map == null)
        {
            map = new SymbolMap();
            fData.put(dataseg, map);

        }

        return map.AddEntry(name, sym);

    }
    
    /**
     * 
     * @param name
     * @param using
     * @return true if the symbol can be found.
     */
    public Boolean ContainsEntry(String name, ArrayList<String> using)
    {
        if (fRoot.get(name) != null) return true;
        
        if (using != null)
        {
            for (Iterator<String> iter = using.iterator(); iter.hasNext();)
            {
                SymbolMap map = fData.get(iter.next());
                if (map.get(name) != null) return true;
            }
        }
        
        return false;
    }
    
    public Symbol FindEntry(String name, ArrayList<String> using) throws LinkError
    {
        Symbol sym;
        Symbol sym2;
        int count = 0;
        // 1 - check if it's globally defined.
        sym = fRoot.get(name);
        if (sym != null) count++;
        
        // 2 - now check if it's also defined for any data segments...
        if (using != null)
        {
            for (Iterator<String> iter = using.iterator(); iter.hasNext();)
            {
                String segname = iter.next();
                SymbolMap map = fData.get(segname);
                if (map != null)
                {
                    sym2 = map.get(name);
                    if (sym2 != null)
                    {
                        count++;
                        sym = sym2;
                    }
                }
            }
        }
        
        // 1 - symbol not found
        if (count == 0)
        {
           return null;
        }
        // 2 - symbol found too many times.
        if (count > 1)
        {
            throw new LinkError(LinkError.E_DUPLICATE, sym, name);
        }
        // 3 - everything's ok.
        return sym;
    }
    
    public Iterator<Entry<String, Symbol>> Iterator()
    {
        return fRoot.entrySet().iterator();
        //return fData.entrySet().iterator();
    }
    
    
}