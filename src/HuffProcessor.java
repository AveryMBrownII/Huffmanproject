import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <p>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 *
 * @author Owen Astrachan
 */

public class HuffProcessor
{
    
    public static final int BITS_PER_WORD = 8;
    public static final int BITS_PER_INT = 32;
    public static final int ALPH_SIZE = (1 << BITS_PER_WORD);
    public static final int PSEUDO_EOF = ALPH_SIZE;
    public static final int HUFF_NUMBER = 0xface8200;
    public static final int HUFF_TREE = HUFF_NUMBER | 1;
    public static final int DEBUG_HIGH = 4;
    public static final int DEBUG_LOW = 1;
    private final int myDebugLevel;
    
    public HuffProcessor()
    {
        this(0);
    }
    
    public HuffProcessor(int debug)
    {
        myDebugLevel = debug;
    }
    
    /**
     * Compresses a file. Process must be reversible and loss-less.
     *
     * @param in  Buffered bit stream of the file to be compressed.
     * @param out Buffered bit stream writing to the output file.
     */
    public void compress(BitInputStream in, BitOutputStream out)
    {
        int[] freqs = readForCounts(in);
        HuffNode tree = makeTreeFromCounts(freqs);
        String[] encodings = makeCodingsFromTree(tree);
        out.writeBits(BITS_PER_INT, HUFF_TREE);
        writeHeader(tree, out);
        writeCompressedBits(encodings, in, out);
        out.close();
    }
    
    private int[] readForCounts(BitInputStream in)
    {
        int[] freqs = new int[ALPH_SIZE + 1];
        while (true)
        {
            int val = in.readBits(BITS_PER_WORD);
            if (val == -1)
            {
                freqs[PSEUDO_EOF] = 1;
                break;
            }
            else
            {
                freqs[val]++;
            }
        }
        
        return freqs;
    }
    
    private HuffNode makeTreeFromCounts(int[] freqs)
    {
        PriorityQueue<HuffNode> pq = new PriorityQueue<>();
        
        for (int i = 0; i < freqs.length; i++)
        {
            if (freqs[i] > 0)
            {
                pq.add(new HuffNode(i, freqs[i], null, null));
            }
        }
        
        while (pq.size() > 1)
        {
            HuffNode left = pq.remove();
            HuffNode right = pq.remove();
            HuffNode t = new HuffNode(left.myValue + right.myValue, left.myWeight + right.myWeight,
                    left, right);
            pq.add(t);
        }
        
        return pq.remove();
    }
    
    private void makeCodingsFromTree(String[] encodings, String encoding, HuffNode node)
    {
        if (node != null)
        {
            // base case: if node is a leaf
            if (node.myLeft == null && node.myRight == null)
            {
                encodings[node.myValue] = encoding;
            }
            else
            {
                makeCodingsFromTree(encodings, encoding + "0", node.myLeft);
                makeCodingsFromTree(encodings, encoding + "1", node.myRight);
            }
        }
    }
    
    private String[] makeCodingsFromTree(HuffNode root)
    {
        String[] encodings = new String[ALPH_SIZE + 1];
        makeCodingsFromTree(encodings, "", root);
        return encodings;
    }
    
    private void writeHeader(HuffNode node, BitOutputStream out)
    {
        if (node != null)
        {
            // if node is internal
            if ((node.myRight != null || node.myLeft != null))
            {
                out.writeBits(1, 0);
                writeHeader(node.myLeft, out);
                writeHeader(node.myRight, out);
            }
            else
            {
                out.writeBits(1, 1);
                out.writeBits(BITS_PER_WORD + 1, node.myValue);
            }
        }
    }
    
    private void writeCompressedBits(String[] encodings, BitInputStream in, BitOutputStream out)
    {
        in.reset();
        while (true)
        {
            int val = in.readBits(BITS_PER_WORD);
            if (val == -1)
            {
                out.writeBits(encodings[PSEUDO_EOF].length(),
                        Integer.parseInt(encodings[PSEUDO_EOF], 2));
                break;
            }
            else
            {
                String code = encodings[val];
                out.writeBits(code.length(), Integer.parseInt(code, 2));
            }
        }
    }
    
    /**
     * Decompresses a file. Output file must be identical bit-by-bit to the
     * original.
     *
     * @param in  Buffered bit stream of the file to be decompressed.
     * @param out Buffered bit stream writing to the output file.
     */
    public void decompress(BitInputStream in, BitOutputStream out)
    {
        int bits = in.readBits(BITS_PER_INT);
        if (bits != HUFF_TREE)
        {
            throw new HuffException("illegal header starts with " + bits);
        }
        HuffNode root = readTreeHeader(in);
        readCompressedBits(root, in, out);
        out.close();
    }
    
    private HuffNode readTreeHeader(BitInputStream in)
    {
        int bit = in.readBits(1);
        if (bit == -1)
        {
            throw new HuffException("invalid bit");
        }
        if (bit == 0)
        {
            HuffNode left = readTreeHeader(in);
            HuffNode right = readTreeHeader(in);
            return new HuffNode(0, 0, left, right);
        }
        else
        {
            int value = in.readBits(BITS_PER_WORD + 1);
            
            return new HuffNode(value, 0, null, null);
        }
    }
    
    private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out)
    {
        HuffNode current = root;
        while (true)
        {
            int bits = in.readBits(1);
            if (bits == -1)
            {
                throw new HuffException("bad input, no PSEUDO_EOF");
            }
            else
            {
                if (bits == 0)
                {
                    current = current.myLeft;
                }
                else
                {
                    current = current.myRight;
                }
                
                if (current != null && current.myRight == null && current.myLeft == null)
                {
                    if (current.myValue == PSEUDO_EOF)
                    {
                        break;
                    }
                    else
                    {
                        out.writeBits(BITS_PER_WORD, current.myValue);
                        current = root;
                    }
                }
            }
        }
    }
}