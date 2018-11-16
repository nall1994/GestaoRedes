

public class InterfaceValues {
    private String ifIndex;
    private String ifDescr;
    private String ifPhysAddress;
    private String inOctets;
    private String outOctets;
    private String difOctets;
    private long tempo_entre_consultas;

    public InterfaceValues(String ifIndex,String ifDescr,String ifPhysAddress,String inOctets, String outOctets,long tempo_entre_consultas) {
        this.ifIndex = ifIndex;
        this.ifDescr = ifDescr;
        this.ifPhysAddress = ifPhysAddress;
        this.inOctets = inOctets;
        this.outOctets = outOctets;
        this.tempo_entre_consultas = tempo_entre_consultas;
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

    public long getTempo_entre_consultas() {
        return tempo_entre_consultas;
    }

}
