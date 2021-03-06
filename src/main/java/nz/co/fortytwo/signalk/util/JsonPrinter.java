package nz.co.fortytwo.signalk.util;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A class to serialize a Map as JSON. This class makes some specific assumptions
 * about the data which allows it to be very fast.
 *
 * 1. The map is a flat map containing only Strings, Numbers, Booleans or null.
 * 2. The keys in the map are hierarchical, so "a.b.c"=1 is {"a": {"b": {"c": 1 }}}
 * 3. The keys are sorted alphabetically
 * 4. The above implies no arrays, and no empty maps.
 */
public class JsonPrinter {
    
    private String indent=null;
    private StringBuilder curindent = new StringBuilder();
    private DecimalFormat[] df = new DecimalFormat[12]; // Up to 12dp for a number.
    
    public JsonPrinter() {
    }

    /**
     * Set the ModelPrinter to pretty-print the output
     * The opposite of {@link #setCompact}
     * @param numspaces the number of spaces to indent each line, from 0..16
     */
    public void setPretty(int numspaces) {
        if (numspaces < 0 || numspaces > 16) {
            throw new IllegalArgumentException();
        }
        indent = "                ".substring(0, numspaces);
    }

    /**
     * Set the ModelPrinter to make the output as compact as possible.
     * The opposite of {@link #setPretty} and the default state.
     */
    public void setCompact() {
        indent = null;
    }

    /**
     * Return the number of decimal-places to use when printing the specified key (as a double)
     * The default is 8 for all keys.
     */
    public int getNumDecimalPlaces(String key) {
        return 8;
    }

    /**
     * Write the values returned from the specified iterator to the specified output. If
     * no values are included then nothing is written, not even "{}"  and this method returns false.
     * A trailing newline is written only if the output is being pretty-printed.
     *
     * @param iterator the iterator containing the data - see class API docs for restrictions
     * @param separator how the keys are separated, eg '.' for keys like "a.b.c"
     * @param out the Appendable to write to
     * @return true if something was written to out
     */
    public boolean write(Iterator<Map.Entry<String,Object>> iterator, char separator, Appendable out) throws IOException {
        curindent.setLength(0);
        boolean begun = false;

        List<String> trail = new ArrayList<String>();       // The "breadcrumbs" of where we are in the tree
        boolean needcomma = false;
        String lastkey = null, lastmissingkey = null;

        while (iterator.hasNext()) {
            Map.Entry<String,Object> e = iterator.next();
            String key = e.getKey();
            Object value = e.getValue();
            if (!begun) {
                jsonBegin(out);
                begun = true;
            }
            String[] s = key.split("\\.");
            int l = s.length;
            if (lastkey != null && (key.compareTo(lastkey) <= 0 || (key.startsWith(lastkey) && key.charAt(lastkey.length()) == separator))) {
                throw new IllegalStateException("Key \""+key+"\" can't follow key \""+lastkey+"\"");
            }

            int j = 0;      // The number of tree entries in common with the previously output value
            while (l > j && trail.size() > j && trail.get(j).equals(s[j])) {
                j++;
            }
            while (trail.size() > j) {
                jsonClose(out);
                needcomma = true;
                trail.remove(trail.size() - 1);
            }
            if (needcomma) {
                needcomma = false;
                jsonComma(out);
            }
            while (l - 1 > j) {
                trail.add(jsonKey(s[j++], out));
                jsonBegin(out);
                needcomma = false;
            }

            if (needcomma) {
                needcomma = false;
                jsonComma(out);
            }
            jsonKey(s[j], out);
            if (value instanceof String) {
                jsonWrite((String)value, out);
            } else if (value instanceof Integer) {
                jsonWrite(((Integer)value).intValue(), out);
            } else if (value instanceof Number) {
                jsonWrite(s[j], ((Number)value).doubleValue(), out);
            } else if (value instanceof Boolean) {
                jsonWrite(((Boolean)value).booleanValue(), out);
            } else if (value== null) {
                jsonNull(out);
            } else {
                throw new IllegalStateException("Can't print value of type \""+value.getClass().getName()+"\" for key \""+key+"\"");
            }
            needcomma = true;
            lastkey = key;
        }
        while (!trail.isEmpty()) {
            jsonClose(out);
            trail.remove(trail.size() - 1);
        }
        if (begun) {
            jsonEnd(out);
        }
        return begun;
    }

    private void jsonWrite(String value, Appendable out) throws IOException {
    	//check for config arrays
    	if(value.startsWith("[") && value.endsWith("]")){
    		jsonWriteArray(value, out);
    		return;
    	}
        out.append('"');
        int len = value.length();
        char c = 0;
        for (int i=0; i<len; i++) {
            char b = c;
            c = value.charAt(i);
            switch (c) {
            case '\\':
            case '"':
                out.append('\\');
                out.append(c);
                break;
            case '/':
                if (b == '<') {
                    out.append('\\');
                }
                out.append(c);
                break;
            case '\b':
                out.append("\\b");
                break;
            case '\t':
                out.append("\\t");
                break;
            case '\n':
                out.append("\\n");
                break;
            case '\f':
                out.append("\\f");
                break;
            case '\r':
                out.append("\\r");
                break;
            default:
                if (c < 0x20 || (c >= 0x80 && c < 0xA0) || c == 0x2028 || c == 0x2029) {
                    String t = Integer.toHexString(c);
                    out.append("\\u");
                    switch(t.length()) {
                        case 1: out.append('0');
                        case 2: out.append('0');
                        case 3: out.append('0');
                    }
                    out.append(t);
                } else {
                    out.append(c);
                }
            }
        }
        out.append('"');
    }

    private void jsonWrite(int value, Appendable out) throws IOException {
        out.append(Integer.toString(value));
    }
    
    private void jsonWriteArray(String value, Appendable out) throws IOException {
        out.append(value);
    }

    private void jsonNull(Appendable out) throws IOException {
        out.append("null");
    }

    private void jsonWrite(String key, double value, Appendable out) throws IOException {
        int dp = getNumDecimalPlaces(key);
        if (df[dp] == null) {
            StringBuilder sb = new StringBuilder(dp + 2);
            sb.append("#0.");
            for (int i=0;i<dp;i++) {
                sb.append('0');
            }
            df[dp] = new DecimalFormat(sb.toString());
        }
        String v = df[dp].format(value);

        // Trim trailing '0' after decimal point for brevity.
        int j = v.length() - 1;
        while (j > 0 && v.charAt(j) == '0') {
            j--;
        }
        if (v.charAt(j) == '.') {
            j++;
        }
        out.append(v.substring(0, j + 1));
    }

    private void jsonWrite(boolean value, Appendable out) throws IOException {
        out.append(value ? "true" : "false");
    }

    private void jsonBegin(Appendable out) throws IOException {
        out.append('{');
        if (indent != null) {
            out.append('\n');
            curindent.append(indent);
            out.append(curindent);
        }
    }

    private void jsonEnd(Appendable out) throws IOException {
        if (indent != null) {
            out.append('\n');
        }
        out.append("}");
        if (indent != null) {
            out.append('\n');
        }
    }

    private String jsonKey(String key, Appendable out) throws IOException {
        jsonWrite(key, out);
        out.append(':');
        if (indent != null) {
            out.append(' ');
        }
        return key;
    }

    private void jsonClose(Appendable out) throws IOException {
        if (indent != null) {
            out.append('\n');
            curindent.setLength(curindent.length() - indent.length());
            out.append(curindent);
        }
        out.append("}");
    }

    private void jsonComma(Appendable out) throws IOException {
        out.append(',');
        if (indent != null) {
            out.append('\n');
            out.append(curindent);
        }
    }

    /*
    public static void main(String[] args) throws Exception {
        TreeMap<String,Object> m = new TreeMap<String,Object>();
        m.put("a.b.d", 1);
        m.put("a.b.c", 2);
        m.put("a.a.c", 2);
        new JSONPrinter().write(m.entrySet().iterator(), '.', System.out);
    }
    */

}

