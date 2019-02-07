import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOScalar;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Variable;

/**
 * This class creates and returns ManagedObjects
 *
 */

public class MOScalarCreator {
    public static MOScalar createReadOnly(OID oid,Object value ){
        return new MOScalar(oid, MOAccessImpl.ACCESS_READ_ONLY, getVariable(value));
    }

    public static MOScalar createWriteRead(OID oid, Object value) {
        MOScalar novo = new MOScalar(oid, MOAccessImpl.ACCESS_READ_WRITE, getVariable(value));
        System.out.println(novo.getAccess());
        return novo;
    }

    

    private static Variable getVariable(Object value) {
        if(value instanceof String) {
            return new OctetString((String)value);
        }
        if(value instanceof Integer) {
            return new Integer32((Integer) value);
        }
        throw new IllegalArgumentException("Unmanaged Type: " + value.getClass());
    }

}

