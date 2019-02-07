import java.awt.*;
import java.util.ArrayList;

public class Agente {
    private String porta;
    private String commun_snmp;
    private ArrayList<String> imagens;

    public Agente(){
        this.porta=null;
        this.commun_snmp=null;
        this.imagens=new ArrayList<>();
    }

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

    public void addImagens(String imagem){
        this.imagens.add(imagem);
    }

    public void prettyPrint(){
        System.out.println("------");
        System.out.println("Porta: "+ this.porta);
        System.out.println("Commun String: "+ this.commun_snmp);
        System.out.println("Lista de imagens:");
        for(String e : this.imagens){
            System.out.println(e);
        }
        System.out.println("------");
    }
}