import java.io.*;

public class SNMPConf {
    private static int numberImages = 0;
    public static void main(String args[]){
        Agente agente = new Agente();
        getInfoConfig("configs/containership-conf.txt",agente);
        getInfoImages("configs/containership-images.txt",agente);
        agente.prettyPrint();
        TestSNMPAgent testAgent = new TestSNMPAgent("udp:127.0.0.1/161");
        try {
            testAgent.runAgent(agente,numberImages);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    /*
    * Esta função serve para ler e carregar a informação do ficheiro containership-conf.txt
    */
    public static void getInfoConfig(String filename,Agente toBeEdit){
        BufferedReader br = null;
        try {
            //Abrir ficheiro para ler
            br = new BufferedReader(new FileReader(filename));

            //Ler linha a linha
            String inputLine = null;
            while((inputLine=br.readLine())!=null){
                //System.out.println(inputLine);
                if(inputLine.contains("udp_port")){
                    String[] parts= inputLine.split("\\s+");
                    String part1= parts[0];
                    String part2= parts[1];
                    toBeEdit.setPorta(part2);
                    //System.out.println(part1);
                    //System.out.println(part2);
                    //System.out.println("Entrei");
                }else if(inputLine.contains("community_string")){
                    String[] parts2= inputLine.split("\\s+");
                    String part3= parts2[0];
                    String part4= parts2[1];
                    toBeEdit.setCommun_snmp(part4);
                }
            }

            //
        }catch(IOException ex){
            System.err.println("An IOException was caught!");
            ex.printStackTrace();
        }

    }

    /*
    * Esta função server para ler e carregar a lista de imagens para o agente
    */
    public static void getInfoImages(String filename,Agente toBeEdit){
        BufferedReader br = null;
        try {
            //Abrir ficheiro para ler
            br = new BufferedReader(new FileReader(filename));

            //Ler linha a linha
            String inputLine = null;
            while((inputLine=br.readLine())!=null){
                //System.out.println(inputLine);
                toBeEdit.addImagens(inputLine);
                numberImages++;
            }

            //
        }catch(IOException ex){
            System.err.println("An IOException was caught!");
            ex.printStackTrace();
        }

    }

}
