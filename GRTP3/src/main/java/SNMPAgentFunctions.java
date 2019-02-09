import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Scanner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.api.*;
import org.snmp4j.agent.ManagedObject;
import org.snmp4j.agent.mo.DefaultMOTable;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOTable;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;

public class SNMPAgentFunctions {
    public SNMPAgent agent = null;
    public SNMPManager client = null;
    public String address;
    private int numberImages;
    private int requests = 0;
    private static final int MAX_REQUESTS_PER_MINUTE = 120;
    private int nContainers;
    private MOTableBuilder builder_images_table;
    private MOTableBuilder builder_containers_table;
    private MOTable images_table;
    private MOTable containers_table;
    static final OID indexParam = new OID("1.3.6.1.3.2019.1.1.0");
    static final OID nameParam = new OID("1.3.6.1.3.2019.1.2.0");
    static final OID flagParam = new OID("1.3.6.1.3.2019.1.3.0");
    static final OID indexIParam = new OID("1.3.6.1.3.2019.1.4.0");
    static final OID statusData = new OID("1.3.6.1.3.2019.4.1.0");
    static final OID contadorContainers = new OID("1.3.6.1.3.2019.4.2.0");

    public SNMPManager getClient() {
        return this.client;
    }

    public void runAgent(Agente agente, int numberImages) throws IOException {
        this.numberImages = numberImages;
        this.init(agente);
        requests = 0;
        Chrono.start();
    }

        /**
         * Constructor
         * @param add
         */
    public SNMPAgentFunctions(String add) {
        address = add;
        nContainers = 0;
    }

    private void init(Agente agente) throws IOException {
        agent = new SNMPAgent("127.0.0.1/" + agente.getPorta());
        agent.start();
        agent.unregisterManagedObject(agent.getSnmpv2MIB());
        ArrayList<String> images = agente.getImagens();

        builder_images_table = new MOTableBuilder(new OID("1.3.6.1.3.2019.2.1"));
        builder_images_table.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING, MOAccessImpl.ACCESS_READ_ONLY);
        for(int i = 0 ; i< images.size(); i++) {
            builder_images_table.adicionarValorEntrada(new OctetString(images.get(i)));
        }
        images_table = builder_images_table.build();

        builder_containers_table = new MOTableBuilder(new OID("1.3.6.1.3.2019.3.1"));
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_ONLY);
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_INTEGER32,MOAccessImpl.ACCESS_READ_ONLY);
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_INTEGER32,MOAccessImpl.ACCESS_READ_WRITE);
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_WRITE);
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_WRITE);

        agent.registerManagedObject(MOScalarCreator.createReadOnly(statusData, LocalDateTime.now().toString()));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(contadorContainers,0));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(indexParam,"None"));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(nameParam,"None"));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(flagParam,0));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(indexIParam,1));
        agent.registerManagedObject(images_table);

        containers_table = builder_containers_table.build();
        agent.registerManagedObject(containers_table);

        // Setup the client to use our newly started agent
        client = new SNMPManager("udp:127.0.0.1/"+agente.getPorta());

    }

    public String getCreatedContainers() {
        double time = Chrono.stop();
        if(time >= 60.0) {
            requests = 1;
            Chrono.start();
        } else {
            requests++;
            if(requests >= MAX_REQUESTS_PER_MINUTE) {
                return "Capacidade do servidor excedida, espere um pouco para submeter o pedido novamente!";
            }
        }
        String result = "";
        try {
            for (int i = 1; i<=nContainers;i++) {
                String containerName = client.getAsString(new OID("1.3.6.1.3.2019.3.1.1." + i));
                String containerID = client.getAsString(new OID("1.3.6.1.3.2019.3.1.5." + i));
                if(i < nContainers) {
                    result += containerName + " -> " + containerID + "\n";
                } else {
                    result += containerName + " -> " + containerID;
                }

            }
        }catch(IOException ex) {
            result = "Ocorreu uma exceção! Tente novamente!";
        }
        return result;
    }

    public String removeContainer(int container_index) {
        try {
            int containerState = Integer.parseInt(client.getAsString(new OID("1.3.6.1.3.2019.3.1.3." + container_index)));
            if(containerState == 2) return "Este container está a ser alterado atualmente! Tente novamente mais tarde!";
        } catch(IOException e) {
            return "Uma exceção ocorreu! Tente novamente por favor";
        }
        double time = Chrono.stop();
        if(time >= 60.0) {
            requests = 1;
            Chrono.start();
        } else {
            requests++;
            if(requests >= MAX_REQUESTS_PER_MINUTE) {
                return "Capacidade do servidor excedida, espere um pouco para submeter o pedido novamente!";
            }
        }
        String result = "";
        try {
            String containerID = client.getAsString(new OID("1.3.6.1.3.2019.3.1.5." + container_index));
            builder_containers_table.atualizarEstado(new OID(""+ container_index),2,containers_table);//changing
            ProcessBuilder pb = new ProcessBuilder("docker","container","stop",containerID);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            builder_containers_table.atualizarEstado(new OID(""+ container_index),5,containers_table);//removing
            ProcessBuilder pb2 = new ProcessBuilder("docker","container","rm",containerID);
            pb2.redirectErrorStream(true);
            Process p2 = pb2.start();
            builder_containers_table.removerLinha(new OID("" + container_index),containers_table);
            result = "Container com ID: " + containerID + " removido com sucesso!";
        }catch(IOException e) {
            result = "Ocorreu uma exceção! Tente novamente!";
        }

        return result;
    }

    public String loadParameters(int image_index) {
        double time = Chrono.stop();
        if(time >= 60.0) {
            requests = 1;
            Chrono.start();
        } else {
            requests++;
            if(requests >= MAX_REQUESTS_PER_MINUTE) {
                return "Capacidade do servidor excedida, espere um pouco para submeter o pedido novamente!";
            }
        }
        String returnResult = "";
        try {
            String imagem_escolhida = client.getAsString(new OID("1.3.6.1.3.2019.2.1.1." + image_index));
            client.setValueString(indexParam,imagem_escolhida);
            client.setValueString(nameParam,imagem_escolhida);
            client.setValueInt(indexIParam,image_index);
            returnResult = "Parâmetros carregados! Já pode criar o container!";
        }catch(IOException e) {
            returnResult = "Uma exceção ocorreu! Tente novamente por favor!";
        }

        return returnResult;
    }

    public String startContainer(int container_index) {
        try {
            int containerState = Integer.parseInt(client.getAsString(new OID("1.3.6.1.3.2019.3.1.3." + container_index)));
            if(containerState == 2) return "Este container está a ser alterado atualmente! Tente novamente mais tarde!";
        } catch(IOException e) {
            return "Uma exceção ocorreu! Tente novamente por favor";
        }
        try {
            builder_containers_table.atualizarEstado(new OID(""+ container_index),2,containers_table);//changing
            String containerID = client.getAsString(new OID("1.3.6.1.3.2019.3.1.5." + container_index));
            ProcessBuilder pb = new ProcessBuilder("docker","run","--detach",containerID);
            Process p = pb.start();
            builder_containers_table.atualizarEstado(new OID(""+ container_index),3,containers_table);//up
            return "Container com ID: " + containerID + " iniciado com sucesso!";
        }catch(IOException e) {
            return("Uma exceção ocorreu! Tente novamente por favor!");
        }
    }

    public String stopContainer(int container_index) {
        try {
            int containerState = Integer.parseInt(client.getAsString(new OID("1.3.6.1.3.2019.3.1.3." + container_index)));
            if(containerState == 2) return "Este container está a ser alterado atualmente! Tente novamente mais tarde!";
        } catch(IOException e) {
            return "Uma exceção ocorreu! Tente novamente por favor";
        }
        try {
            builder_containers_table.atualizarEstado(new OID(""+ container_index),2,containers_table);//changing
            String containerID = client.getAsString(new OID("1.3.6.1.3.2019.3.1.5." + container_index));
            ProcessBuilder pb = new ProcessBuilder("docker","stop",containerID);
            Process p = pb.start();
            builder_containers_table.atualizarEstado(new OID(""+ container_index),4,containers_table);//down
            return "Container com ID: " + containerID + " parado com sucesso!";
        }catch(IOException e) {
            return("Uma exceção ocorreu! Tente novamente por favor!");
        }
    }

    public String create() {
        double time = Chrono.stop();
        if(time >= 60.0) {
            requests = 1;
            Chrono.start();
        } else {
            requests++;
            if(requests >= MAX_REQUESTS_PER_MINUTE) {
                return "Capacidade do servidor excedida, espere um pouco para submeter o pedido novamente!";
            }
        }
        String result = "";
        try {
            String testeParam = client.getAsString(indexParam);
            String nomeContainer = client.getAsString(nameParam);
            if(testeParam.equalsIgnoreCase("None")) {
                result = "Tem que carregar os parâmetros de uma imagem primeiro!";
            } else {
                int indexImage = Integer.parseInt(client.getAsString(indexIParam));
                int numero_containers_criados = Integer.parseInt(client.getAsString(contadorContainers));
                builder_containers_table.adicionarEntrada(containers_table,nomeContainer,indexImage,1,"0%","changing");
                client.setValueInt(contadorContainers,numero_containers_criados+1);
                client.setValueString(indexParam,"None");
                client.setValueString(nameParam,"None");
                client.setValueInt(indexIParam,0);
                result = criarContainer(testeParam, nomeContainer, builder_containers_table, containers_table);
            }
        }catch(IOException e) {
            result = "Ocorreu uma exceção! Tente novamente por favor!";
        }


        return result;
    }


    private String criarContainer(String imagem, String nomeContainer,MOTableBuilder tabelaBuilder,MOTable tabela) throws IOException{
        ProcessBuilder builder = new ProcessBuilder("docker","create",imagem);
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        String tempLine="";
        while (true){
            line = r.readLine();
            if(line!=null) {
                tempLine = line;

            } else {
                break;
            }
            System.out.println(line);
        }
        nContainers++;
        ProcessBuilder correr = new ProcessBuilder("docker","run","--detach",tempLine);
        correr.redirectErrorStream(true);
        Process p1 = correr.start();
        OID statusOID = new OID("1.3.6.1.3.2019.3.1.3." + nContainers);
        tabelaBuilder.atualizarEstado(new OID(""+ nContainers),3,tabela);
        tabelaBuilder.atualizarID(new OID("" + nContainers),tempLine,tabela);
        return tempLine;
    }

    public String listContainers() {
        double time = Chrono.stop();
        if(time >= 60.0) {
            requests = 1;
            Chrono.start();
        } else {
            requests++;
            if(requests >= MAX_REQUESTS_PER_MINUTE) {
                return "Capacidade do servidor excedida, espere um pouco para submeter o pedido novamente!";
            }
        }
        try {
            return verificarContainers();
        } catch(IOException e ){
            return "Ocorreu uma exceção! Tente novamente por favor!";
        }

    }

    public String verificarContainers() throws IOException{
        int nContainers = Integer.parseInt(client.getAsString(contadorContainers));
        String nomeCont = "";
        String idCont = "";
        int status = 0;
        String statusTo = "";
        String novaString = "Container: ";
        String toBePassed= "\n Lista de Containers:\n";
        for(int i =0; i< nContainers;i++){
             nomeCont= client.getAsString(new OID("1.3.6.1.3.2019.3.1.1."+(i+1)));
             idCont = client.getAsString(new OID("1.3.6.1.3.2019.3.1.5." + (i+1)));
             status = Integer.parseInt(client.getAsString(new OID("1.3.6.1.3.2019.3.1.3."+(i+1))));
             if (status == 1){
                statusTo = "creating";
             }else if (status == 2){
                 statusTo = "changing";
             }else if (status == 3){
                 statusTo = "up";
             }else if(status == 4){
                 statusTo = "down";
             }else if(status == 5){
                 statusTo = "removing";
             }
             toBePassed = toBePassed + novaString + nomeCont + "(" + idCont + ") ---- Status: " + statusTo + "\n";
        }
        toBePassed += "\nOperação concluída com sucesso";
        return toBePassed;
    }

    public boolean verificaContainer(){
        String value="None";
        try {
            value = client.getAsString(nameParam);
        }catch (IOException e){
            e.printStackTrace();
        }
        if(value.equals("None")){
            return false;
        }
        return true;
    }

}

