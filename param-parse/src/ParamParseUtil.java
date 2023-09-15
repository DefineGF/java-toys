import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


public class ParamParseUtil {
    // "变量名"正则
    private final static String VAR_NAME_REG      = "^[a-zA-Z_$][a-zA-Z\\d_$]*$";
    // "${变量名}.{数字}" 正则
    private final static String VAR_POINT_NUM_REG = "^[a-zA-Z_$][a-zA-Z\\d_$]*\\.[0-9]+$";
    // "${变量名}.{变量名}" 正则
    private final static String VAR_POINT_VAR_REG = "^[a-zA-Z_$][a-zA-Z\\d_$]*\\.[a-zA-Z_$][a-zA-Z\\d_$]*$";


    // Pod.class
    // Person.class return Person
    public static <T> T parse(Class<T> clz, String queryString) throws Exception {
        if (queryString == null || queryString.length() == 0) return null;

        T t = clz.newInstance();
        List<String[]> data = splitString(queryString);
        data.sort(Comparator.comparing(a -> a[0]));
        mainWorkStream(t, clz, data);
        return t;
    }

    /**
     * 拆分整个字符串：
     *   1. 以 ”&“ 为分割符，获取键值对；
     *   2. 过滤掉首字母小写的参数；
     *   3. 对键值对进行分割；
     *   4. 过滤掉非键值对的数据；
     *   5. 转换成 List::String[] {key, value} 的形式
     */
    public static List<String[]> splitString(String content) {
        String[] tokens = content.split("&");
        return Arrays.stream(tokens)
                .filter(token -> token.length() > 0 && Character.isUpperCase(token.charAt(0)))  // 过滤首字母小写的参数
                .map(token -> token.split("="))
                .filter(temp -> temp.length == 2)
                .collect(Collectors.toList());
    }


    /**
     * 主要工作流，类似于责任链的设计模式，每个步骤从全局数据中选择数据进行处理，然后从全局数据中删除，
     * 流程大致分为四个步骤：
     * 1. 处理基本类型：比如 Cpu=val
     * 2. 处理泛型类型为基本类型的List, 比如 Command.1=/bin/bash
     * 3. 处理复合数据类型：比如 Metadata.Generation=1; 当然更复杂的情况递归调用 `mainWorkStream` 即可
     * 4. 处理复合数据类型的 list 参数，比如 Container.5.Environment.1.Key=PORT; 复杂情况递归调用即可
     *
     */
    private static void mainWorkStream(Object target, Class mainClz, List<String[]> data) {

        try {
            handleBasicField(target, mainClz, data);            // P
            handleBasicListField(target, mainClz, data);        // P.[num]
            handleVarAndVarFormatField(target, mainClz, data);  // P.P.xxx
            handleVarAndNumFormatField(target, mainClz, data);  // P.[num].xxx
        } catch (IllegalAccessException | InstantiationException | NoSuchFieldException | ClassNotFoundException e) {
            e.printStackTrace();
            System.out.println("[ERROR] => error occur when parsing!" + e.getMessage());
        }

    }

    /**
     * 处理基本的数据类型，比如 name="cheng", 直接获取 field 并设置值即可
     * @param target: 当前类的实例化对象
     * @param mainClz: 当前类的 Class 对象
     * @param data: 全局数据流
     */
    private static void handleBasicField(Object target, Class mainClz, List<String[]> data) {
        // 处理基本数据类型
        List<String[]> basicVarData = data.stream()
                .filter(temp -> temp[0].matches(VAR_NAME_REG)
                        && Character.isUpperCase(temp[0].charAt(0)))
                .collect(Collectors.toList());

        for (String[] kv : basicVarData) {
            Field field = getDeclaredFieldByName(mainClz, lowerCaseFirstChar(kv[0]));
            if (field != null && checkFieldAccessible(field)) {

                Object fieldValObj = getFieldValByType(field.getType(), kv[1]);
                if (fieldValObj != null) {
                    try {
                        field.set(target, fieldValObj);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }

            }
        }
        data.removeAll(basicVarData); // 删除处理过的元素
    }


    /**
     * 处理泛型参数是基本数据类型的 List 成员变量
     */
    private static void handleBasicListField(Object target, Class mainClz, List<String[]> data)
            throws IllegalAccessException, InstantiationException {
        List<String[]> basicListData = data.stream()
                .filter(temp -> temp[0].matches(VAR_POINT_NUM_REG)
                        && Character.isUpperCase(temp[0].charAt(0)))
                .collect(Collectors.toList());

        for (String[] kv : basicListData) {
            String fieldName = kv[0].substring(0, kv[0].indexOf("."));
            Field listField = getDeclaredFieldByName(mainClz, lowerCaseFirstChar(fieldName));

            if (listField == null || !checkFieldAccessible(listField)
                    || listField.getType() != List.class
                    || !(listField.getGenericType() instanceof ParameterizedType)) {
                return;
            }
            // 获取 List 类型的 Field 的实例
            List listFieldVal = (List)listField.get(target);
            if (listFieldVal == null) {
                List tempList = ArrayList.class.newInstance();
                listField.set(target, tempList);
                listFieldVal = tempList;
            }

            // 将数据添加到 list 中
            ParameterizedType parameterizedType = (ParameterizedType) (listField.getGenericType());
            Type argumentType = parameterizedType.getActualTypeArguments()[0];
            listFieldVal.add(getFieldValByType(argumentType, kv[1]));
        }
        data.removeAll(basicListData);
    }


    /**
     * 处理以 ${变量名}.${变量名} 开头的形式
     */
    private static void handleVarAndVarFormatField(Object target, Class mainClz, List<String[]> data)
            throws NoSuchFieldException, IllegalAccessException,
            InstantiationException, ClassNotFoundException {
        // 从全局数据中筛选符合以 ${变量名}.${变量名} 开头的数据
        List<String[]> varAndVarData = data.stream()
                .filter(temp -> temp[0].split("\\.").length > 1)
                .filter(temp -> isPrefixMatchTarget(temp[0], VAR_POINT_VAR_REG))
                .collect(Collectors.toList());

        for (String[] kv : varAndVarData) {
            String fieldName = lowerCaseFirstChar(kv[0].substring(0, kv[0].indexOf(".")));
            Field field = getDeclaredFieldByName(mainClz, fieldName);

            if (field == null || !checkFieldAccessible(field)) {
                continue;
            }
            // 截取新的数据
            String newKey = kv[0].substring(kv[0].indexOf(".") + 1);
            List<String[]> list = Arrays.stream(new String[][]{new String[] {newKey, kv[1]}})
                    .collect(Collectors.toList());

            // 获取 Field 实例化内容，没有则通过 Class 实例化并保存
            Object fieldValObj = field.get(target);
            if (fieldValObj == null) {
                Object obj = field.getType().newInstance();
                field.set(target, obj);
                fieldValObj = obj;
            }
            mainWorkStream(fieldValObj, field.getType(), list);
        }

        // 删除处理过的数据
        data.removeAll(varAndVarData);
    }


    /**
     * 处理以 ${变量名}.${整型下标} 的形式
     */
    private static void handleVarAndNumFormatField(Object target, Class mainClz, List<String[]> data)
            throws NoSuchFieldException, IllegalAccessException, InstantiationException, ClassNotFoundException {
        // 获得 变量名.数字 开头的形式的内容
        List<String[]> tempList = data.stream()
                .filter(temp -> temp[0].split("\\.").length > 2)
                .filter(temp -> isPrefixMatchTarget(temp[0], VAR_POINT_NUM_REG))
                .collect(Collectors.toList());

        // 首先对变量名进行分组
        Map<String, List<String[]>> groupByNameMap = tempList.stream()
                .collect(Collectors.groupingBy(temp -> temp[0].substring(0, temp[0].indexOf("."))));

        for (String name : groupByNameMap.keySet()) {
            List<String[]> sameNameList = groupByNameMap.get(name);
            Field field = getDeclaredFieldByName(mainClz, lowerCaseFirstChar(name));

            if (field == null || !checkFieldAccessible(field) || field.getType() != List.class ||
                    !(field.getGenericType() instanceof ParameterizedType)) {
                continue;
            }

            // 获取 List 类型的 Field 的实例, 空的话初始化为 ArrayList 形式
            List listFieldVal = (List) field.get(target);
            if (listFieldVal == null) {
                List temp = ArrayList.class.newInstance();
                field.set(target, temp);
                listFieldVal = temp;
            }

            // 根据下标进行分组，对应列表中的不同实例
            Map<String, List<String[]>> groupByIndexMap = sameNameList.stream()
                    .collect(Collectors.groupingBy(temp -> {
                        int i = temp[0].indexOf(".");
                        int j = temp[0].indexOf(".", i + 1);
                        return temp[0].substring(i + 1, j);
                    }));

            for (String index : groupByIndexMap.keySet()) {

                Type genericType = field.getGenericType();
                if (genericType instanceof ParameterizedType) {
                    // 将数据添加到 list 中
                    ParameterizedType parameterizedType = (ParameterizedType) genericType;
                    Type argumentType = parameterizedType.getActualTypeArguments()[0];
                    // 通过 list 的泛型参数类型 (argumentType) 实例化
                    Class typeCls =  Class.forName(argumentType.getTypeName());
                    Object typeValObj = typeCls.newInstance();
                    listFieldVal.add(typeValObj);

                    // 删掉 ${变量名}.${整型下标} 开头后，生成新的键值对
                    List<String[]> sameIndexList = groupByIndexMap.get(index);
                    sameIndexList = sameIndexList.stream()
                            .map(temp -> new String[]
                                    { temp[0].substring(name.length() + index.length() + 2), temp[1]})
                            .collect(Collectors.toList());
                    mainWorkStream(typeValObj, typeCls, sameIndexList);
                }
            }
        }
        data.removeAll(tempList);
    }


    /**
     * 工具方法，判断输入字符串起始格式是否满足输入的正则要求
     */
    private static boolean isPrefixMatchTarget(String input, String reg) {
        int i = input.indexOf(".");
        int j = input.indexOf(".", i + 1);
        return input.substring(0, j == -1 ? input.length() : j).matches(reg);
    }

    /**
     * 完成 Field 权限验证功能：
     *   1. 判断 Field 是否被 SkipMappingValueAnnotation 注解；
     *   2. 判断 Field 的访问限制并设置为 可访问；
     */
    private static boolean checkFieldAccessible(Field field) {
        if (field.getDeclaredAnnotation(SkipMappingValueAnnotation.class) != null) {
            return false;
        }
        if (!Modifier.isPublic(field.getModifiers())) {
            field.setAccessible(true);
        }
        return true;
    }


    private final static Set<Class> WRAPPER_CLASSES = new HashSet<>(Arrays.asList(new Class[]{
            Byte.class, Boolean.class, Character.class, Short.class, Integer.class,
            Long.class, Float.class, Double.class, String.class, BigDecimal.class
    }));

    private final static Set<Class> BASIC_CLASSES = new HashSet<>(Arrays.asList(new Class[] {
            byte.class, boolean.class, char.class, short.class,
            int.class, long.class, float.class, double.class
    }));

    /**
     * 通过指定 Field 参数类型，从字符串中获得Field的值
     */
    private static Object getFieldValByType(Type type, String fieldVal) {
        if (WRAPPER_CLASSES.contains(type)) {
            if (fieldVal == null || fieldVal.length() == 0 || "null".equalsIgnoreCase(fieldVal)) {
                return null;
            }
            if (type == String.class) {
                return fieldVal;
            } else if (type == BigDecimal.class) {
                return new BigDecimal(fieldVal);
            }
        } else if (BASIC_CLASSES.contains(type)) {
            if (("null".equalsIgnoreCase(fieldVal)) || fieldVal == null || fieldVal.length() == 0) {
                // 设置为默认值
                if (type == char.class) {
                    return '\u0000';
                } else if (type == byte.class || type ==short.class
                        || type == int.class || type == long.class) {
                    return 0;
                } else if (type == float.class) {
                    return 0.0f;
                } else if (type == double.class) {
                    return 0.0d;
                } else if (type == boolean.class) {
                    return false;
                }
            }
        } else {
            System.out.println("[ERROR] 未知类型 " + type.getTypeName());
            return null;
        }

        Object ans = null;
        try {
            if (type == Boolean.class || type == boolean.class) {
                ans = "true".equalsIgnoreCase(fieldVal);
            } else if (type == Byte.class || type == byte.class) {
                ans = Byte.parseByte(fieldVal);
            } else if (type == Short.class || type == short.class) {
                ans =  Short.parseShort(fieldVal);
            } else if (type == Character.class || type == char.class) {
                ans = fieldVal.charAt(0);
            } else if (type == Integer.class || type == int.class) {
                ans = Integer.parseInt(fieldVal);
            } else if (type == Long.class || type == long.class) {
                ans =  Long.parseLong(fieldVal);
            } else if (type == Float.class || type == float.class) {
                ans = Float.parseFloat(fieldVal);
            } else if (type == Double.class || type == double.class) {
                ans = Double.parseDouble(fieldVal);
            }
        } catch (NumberFormatException numberFormatException) {
            System.out.println("字符串 " + fieldVal + " 匹配错误: " + numberFormatException);
        }
        return ans;
    }

    /**
     * 工具函数：将字符串首字母小写并返回处理化后的整个字符串
     */
    private static String lowerCaseFirstChar(String data) {
        if (data == null || data.contains(".")) return data;
        char[] chs = data.toCharArray();
        chs[0] = Character.toLowerCase(chs[0]);
        return String.valueOf(chs);
    }

    /**
     * 通过 field 名字获取 Field，用于捕获全局出现的 NoSuchFieldException
     */
    private static Field getDeclaredFieldByName(Class clz, String fieldName) {
        if (fieldName == null || fieldName.length() == 0) return null;
        Field field = null;
        try {
            field = clz.getDeclaredField(lowerCaseFirstChar(fieldName));
        } catch (NoSuchFieldException e) {
            System.out.println("[WARNING] => " + clz + " has not field named: " + fieldName);
        }
        return field;
    }
}
