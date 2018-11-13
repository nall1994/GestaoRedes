

public class InterfaceValues {
    private String ifIndex;
    private String ifDescr;
    private String ifPhysAddress;
    private String inOctets;
    private String outOctets;
    private String difOctets;

    public InterfaceValues(String ifIndex,String ifDescr,String ifPhysAddress,String inOctets, String outOctets) {
        this.ifIndex = ifIndex;
        this.ifDescr = ifDescr;
        this.ifPhysAddress = ifPhysAddress;
        this.inOctets = inOctets;
        this.outOctets = outOctets;
        this.difOctets = String.valueOf(Long.parseLong(outOctets) - Long.parseLong(inOctets));
    }

    public String getIfIndex() {
        return ifIndex;
    }

    public String getIfDescr() {
        return ifDescr;
    }

    public String getIfPhysAddress() {
        return ifPhysAddress;
    }

    public String getInOctets() {
        return inOctets;
    }

    public String getOutOctets() {
        return outOctets;
    }

    public String getDifOctets() {
        return difOctets;
    }
}
