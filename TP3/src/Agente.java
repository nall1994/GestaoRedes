import java.util.ArrayList;

public class Agente {
    private String porta;
    private String commun_snmp;
    private ArrayList<String> imagens;

    public Agente(String porta,String commun_snmp,ArrayList<String> imgs){
        this.porta = porta;
        this.commun_snmp=commun_snmp;
        this.imagens=imgs;
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

    public ArrayList<String> getImagens() {
        return imagens;
    }

    public void setImagens(ArrayList<String> imagens) {
        this.imagens = imagens;
    }
}