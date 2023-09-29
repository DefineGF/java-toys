package anno.orm;

import anno.orm.annos.DBTable;
import anno.orm.annos.SQLInteger;
import anno.orm.annos.SQLString;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class TableCreator {
    public static void main(String[] args) {
        testCreateTable();
    }

    public static void testCreateTable() {
        Class<?> cl = Member.class;
        DBTable table = cl.getAnnotation(DBTable.class);
        if (table == null) {
            System.out.println("No DBTable annotation in class " + cl.getName());
            return;
        }
        String tableName = table.name();
        if (tableName.length() < 1) {
            tableName = cl.getName().toUpperCase(); // 如果获取名字为空， 使用 class name
        }
        List<String> colDefs = new ArrayList<>();
        for (Field field : cl.getDeclaredFields()) {
            String colName = null;
            Annotation[] anns = field.getDeclaredAnnotations();
            if (anns.length < 1) {
                continue; // 不是数据库表字段
            }

            if (anns[0] instanceof SQLInteger) {
                SQLInteger sInt = (SQLInteger) anns[0];
                if (sInt.name().length() < 1) {
                    colName = field.getName().toUpperCase(); // 如果 name 未指定，就使用字段名
                } else {
                    colName = sInt.name();
                }
                String def = colName + " INT" + ORMUtil.getConstraints(sInt.constraints());
                colDefs.add(def);
                System.out.println("current field: " + field.getName() + ", get sql string: " + def);
            }

            if (anns[0] instanceof SQLString) {
                SQLString sString = (SQLString) anns[0];
                if (sString.name().length() < 1 ) {
                    colName = field.getName().toUpperCase();
                } else {
                    colName = sString.name();
                }
                String def = colName + " VARCHAR(" + sString.value() + ")" + ORMUtil.getConstraints(sString.constraints());
                colDefs.add(def);
                System.out.println("current field: " + field.getName() + ", get sql string: " + def);
            }

        }
        // 合并结果
        StringBuilder createCommand = new StringBuilder("CREATE TABLE " + tableName + "(");
        for (String colDef : colDefs) {
            createCommand.append("\n    ").append(colDef).append(",");
        }
        String tableCreate = createCommand.substring(0, createCommand.length() - 1) + ");"; // 去除最后 ","
        System.out.println("finally get SQL string: " + tableCreate);
    }
}
