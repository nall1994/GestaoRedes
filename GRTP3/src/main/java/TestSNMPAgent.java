import java.io.IOException;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.Scanner;

import org.snmp4j.agent.ManagedObject;
import org.snmp4j.agent.mo.DefaultMOTable;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOTable;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;

public class TestSNMPAgent {
    public SNMPAgent agent = null;
    public SNMPManager client = null;
    public String address;
    private int numberImages;

    static final OID indexParam = new OID("1.3.6.1.3.2019.1.1.0");
    static final OID nameParam = new OID("1.3.6.1.3.2019.1.2.0");
    static final OID flagParam = new OID("1.3.6.1.3.2019.1.3.0");
    static final OID indexIParam = new OID("1.3.6.1.3.2019.1.4.0");


    public void runAgent(Agente agente, int numberImages) throws IOException {
        this.numberImages = numberImages;
        this.init(agente);
    }

        /**
         * Constructor
         * @param add
         */
    public TestSNMPAgent(String add) {
        address = add;
    }

    private void init(Agente agente) throws IOException {
        agent = new SNMPAgent("0.0.0.0/" + agente.getPorta());
        agent.start();
        agent.unregisterManagedObject(agent.getSnmpv2MIB());
        ArrayList<String> images = agente.getImagens();
        MOTableBuilder builder = new MOTableBuilder(new OID("1.3.6.1.3.2019.2.1"));
        builder.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING, MOAccessImpl.ACCESS_READ_ONLY);
        for(int i = 0 ; i< images.size(); i++) {
            builder.adicionarValorEntrada(new OctetString(images.get(i)));
        }
        MOTable tabela_imagens = builder.build();
        agent.registerManagedObject(MOScalarCreator.createWriteRead(indexParam,"None"));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(nameParam,"None"));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(flagParam,0));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(indexIParam,1));
        agent.registerManagedObject(tabela_imagens);
        // Setup the client to use our newly started agent
        client = new SNMPManager("udp:127.0.0.1/"+agente.getPorta());
        boolean terminateSystem = false;
        Scanner scanner = new Scanner(System.in);
        while(!terminateSystem) {
            printMenu();
            int escolha = scanner.nextInt();
            switch(escolha) {
                case 1:
                    printContainersMenu(client);
                    int containerIndex = scanner.nextInt();
                    //Ir buscar info da imagem com o containerIndex.
                    String imagem_escolhida = client.getAsString(new OID("1.3.6.1.3.2019.2.1.1." + containerIndex));
                    System.out.println("A criar container com a imagem: " + imagem_escolhida + ".....");
                    client.setValueString(indexParam,imagem_escolhida);
                    client.setValueString(nameParam,imagem_escolhida);
                    client.setValueInt(indexIParam,containerIndex);
                    break;
                case 2:
                    String testeParam = client.getAsString(indexParam);
                    if(testeParam == "None") {
                        System.out.println("Nenhum container está carregado!");
                    } else {
                        int index_inTable = Integer.parseInt(client.getAsString(indexIParam));
                        System.out.println("=============");
                        System.out.println("Informações Atuais no container");
                        System.out.println("Nome do Container: "+ testeParam + " com o indice "+ index_inTable +" na tabela de imagens.");
                        System.out.println("=============");
                    }

                    //Pegar no container dos containerParam e criá-lo com o docker e adicionar à tabela
                    //de containershipContainersTable
                    //Na função que faz isto temos que verificar se está algum container carregado.
                    //faz-se o getAsString do nameParam e se for "None" quer dizer que não está nada carregado.
                    break;
                case 3:
                    terminateSystem = true;
                    break;
                default:
                    System.out.println("Escolha não reconhecida!");
            }
        }
        System.exit(0);

    }

    private void printMenu() {
        System.out.println("-----CONTAINERS MENU-----");
        System.out.println("Escolha a sua opção (1,2 ou 3):");
        System.out.println("1 - Carregar parâmetros de um container.");
        System.out.println("2 - Criar o container carregado.");
        System.out.println("3 - Sair do sistema.");
    }

    private void printContainersMenu(SNMPManager client) {
        try {
            System.out.println("Escolha o container do qual quer carregar os parâmetros para criação:");
            for(int i = 1; i <= numberImages;i++) {
                System.out.println(i + " - " + client.getAsString(new OID("1.3.6.1.3.2019.2.1.1." + i)));
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}

