public class Agente {
    private String porta;
    private String commun_snmp;

    public Agente(String porta,String commun_snmp){
        this.porta = porta;
        this.commun_snmp=commun_snmp;
    }

    public String getPorta() {
        return porta;
    }

    public void setPorta(String porta) {
        this.porta = porta;
    }

    public String getCommun_snmp() {
        return commun_snmp;
    }

    public void setCommun_snmp(String commun_snmp) {
        this.commun_snmp = commun_snmp;
    }
}