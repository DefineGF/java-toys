package anno.orm.annos;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/*
使用例子：

@SQLString(value = 30)
String firstName; // 使用默认 constraints

if (anns[0] instanceof SQLString) {
    SQLString sString = (SQLString) anns[0];
    if (sString.name().length() < 1 ) {
        colName = field.getName().toUpperCase();
    } else {
        colName = sString.name();
    }
    String def = colName + " VARCHAR(" + sString.value() + ")" + getConstraints(sString.constraints());
} // FIRSTNAME VARCHAR(30)

@SQLString(value = 30, constraints = @Constraints(primaryKey = true)) // 指定 constraints
String reference; // 获取同上 REFERENCE VARCHAR(30) PRIMARY KEY
 */

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SQLString {
    int value() default 0;
    String name() default "";
    Constraints constraints() default @Constraints;
}
