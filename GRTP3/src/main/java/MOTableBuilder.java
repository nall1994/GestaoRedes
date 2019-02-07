import java.util.ArrayList;
import java.util.List;

import org.snmp4j.agent.MOAccess;
import org.snmp4j.agent.mo.DefaultMOMutableRow2PC;
import org.snmp4j.agent.mo.DefaultMOTable;
import org.snmp4j.agent.mo.MOColumn;
import org.snmp4j.agent.mo.MOMutableTableModel;
import org.snmp4j.agent.mo.MOTable;
import org.snmp4j.agent.mo.MOTableIndex;
import org.snmp4j.agent.mo.MOTableSubIndex;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.Variable;

public class MOTableBuilder {
    private MOTableSubIndex[] subIndices = new MOTableSubIndex[] {new MOTableSubIndex(SMIConstants.SYNTAX_INTEGER)};
    private MOTableIndex indexDef = new MOTableIndex(subIndices,false);
    private final List<MOColumn> colunas = new ArrayList<>();
    private final List<Variable[]> linhas = new ArrayList<>();
    private int linha_atual = 0;
    private int coluna_atual = 0;

    private OID raiz_tabela;

    private int contador_tipo_coluna = 0;

    public MOTableBuilder(OID oid) {
        raiz_tabela = oid;
    }

    public MOTableBuilder adicionarColuna(int syntax,MOAccess acesso) {
        contador_tipo_coluna++;
        colunas.add(new MOColumn(contador_tipo_coluna,syntax,acesso));
        return this;
    }

    public MOTableBuilder adicionarValorEntrada(Variable variavel) {
        if(linhas.size() == linha_atual) {
            linhas.add(new Variable[colunas.size()]);
        }
        linhas.get(linha_atual)[coluna_atual] = variavel;
        coluna_atual++;
        if(coluna_atual >= colunas.size()) {
            linha_atual++;
            coluna_atual = 0;
        }
        return this;
    }

    public MOTable build() {
        DefaultMOTable tabela = new DefaultMOTable(raiz_tabela,indexDef,colunas.toArray(new MOColumn[0]));
        MOMutableTableModel modelo = (MOMutableTableModel) tabela.getModel();
        int i = 1;
        for(Variable[] variables : linhas) {
            modelo.addRow(new DefaultMOMutableRow2PC(new OID(String.valueOf(i)),variables));
            System.out.println(modelo.getRow(new OID("1")));
            System.out.println("Linha " + i + " - " +variables[0].toString());
            i++;
        }
        tabela.setVolatile(true);
        return tabela;
    }
}
