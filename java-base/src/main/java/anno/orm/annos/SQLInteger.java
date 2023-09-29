package anno.orm.annos;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/*
使用例子：

@SQLInteger
Integer age;

// 反射获取
if (anns[0] instanceof SQLInteger) {
    SQLInteger sInt = (SQLInteger) anns[0];
    if (sInt.name().length() < 1) {
        colName = field.getName().toUpperCase(); // 如果 name 未指定，就使用字段名
    } else {
        colName = sInt.name();
    }
	String def = colName + " INT" + getConstraints(sInt.constraints());
} // AGE INT
 */

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SQLInteger {
    String name() default "";
    Constraints constraints() default @Constraints;
}

