package anno.orm;

import anno.orm.annos.Constraints;
import anno.orm.annos.DBTable;
import anno.orm.annos.SQLInteger;
import anno.orm.annos.SQLString;

@DBTable(name = "MEMBER")
public class Member {
    @SQLString(value = 30)
    public String firstName;

    @SQLString(value = 50)
    public String lastName;

    @SQLInteger
    public Integer age;

    @SQLString(value = 30, constraints = @Constraints(primaryKey = true))
    public String reference;
}
