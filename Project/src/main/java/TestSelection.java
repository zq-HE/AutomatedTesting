import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.cha.CHACallGraph;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.config.AnalysisScopeReader;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @description:
 * 方法类，是一个用来保存一个方法信息的数据结构。
 * 保存有：①class_name 方法所属类名  ②signature 方法签名  ③calls 此方法所依赖的方法集合
 **/
class Method{
    private final String class_name;
    private final String signature;
    private final ArrayList<Method> calls = new ArrayList<Method>();
    public Method(String class_name,String signature){
        this.signature = signature;
        this.class_name = class_name;
    }
    public String getClass_name() {
        return class_name;
    }
    public String getSignature() {
        return signature;
    }
    public ArrayList<Method> getCalls() {
        return calls;
    }
}

/**
 * @description:
 * 启动类，包含主要逻辑和main函数。
 **/
public class TestSelection {
    public static void main(String[]args){
        //获取输入的参数
        final String targetDir = args[1];
        final String classesDir = targetDir+"\\classes";
        final String test_classesDir = targetDir+"\\test-classes";
        final String changeInfoDir = args[2];
        final String runType = args[0];

        try {
            // 得到所有.class文件
            ArrayList<File> classes = new ArrayList<File>();
            getClasses(classesDir,classes);
            ArrayList<File> test_classes = new ArrayList<File>();
            getClasses(test_classesDir,test_classes);
            //初始化
            init();
            //创建scope
            AnalysisScope scope = AnalysisScopeReader.readJavaScope(getJarPath()+"\\scope.txt",
                    new File(getJarPath()+"\\exclusion.txt"), ClassLoader.getSystemClassLoader());
            //加入测试类到scope
            ArrayList<String> testSignature = new ArrayList<String>();
            for(File clazz:test_classes){
                scope.addClassFileToScope(ClassLoaderReference.Application,clazz);
            }

            //创建构件层次图，识别所有测试方法,将其signature加入到arraylist
            ClassHierarchy cha = ClassHierarchyFactory.makeWithRoot(scope);
            CHACallGraph chaCG = new CHACallGraph(cha);
            chaCG.init(new AllApplicationEntrypoints(scope,cha));
            for(CGNode node : chaCG){
                //剔除java原生类
                if(node.getMethod() instanceof ShrikeBTMethod){
                    ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                    if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                        String signature = method.getSignature();
                        testSignature.add(signature);
                    }
                }
            }

            //加入生产类
            for(File clazz:classes){
                scope.addClassFileToScope(ClassLoaderReference.Application,clazz);
            }
            cha = ClassHierarchyFactory.makeWithRoot(scope);
            chaCG = new CHACallGraph(cha);
            chaCG.init(new AllApplicationEntrypoints(scope,cha));

            //通过层次图，得到方法依赖信息
            ArrayList<Method> methods = new ArrayList<Method>();
            ArrayList<Method> testMethods = new ArrayList<Method>();
            ArrayList<Method> proMethods= new ArrayList<Method>();
            for(CGNode node : chaCG){
                if (node.getMethod() instanceof ShrikeBTMethod) {
                    ShrikeBTMethod method = (ShrikeBTMethod) node.getMethod();
                    if ("Application".equals(method.getDeclaringClass().getClassLoader().toString())) {
                        //获取类信息
                        String classInnerName = method.getDeclaringClass().getName().toString();
                        //获取方法签名
                        String signature = method.getSignature();
                        //新建Method
                        Method newMethod = new Method(classInnerName,signature);
                        //初始化调用信息
                        Collection<CallSiteReference> callSites = method.getCallSites();
                        for (CallSiteReference callSiteReference : callSites) {
                            String callMethod_signature = callSiteReference.getDeclaredTarget().getSignature();
                            String callClass_name = callSiteReference.getDeclaredTarget().getDeclaringClass().getName().toString();
                            callClass_name = callClass_name.split("\\$")[0];
                            newMethod.getCalls().add(new Method(callClass_name,callMethod_signature));
                        }
                        methods.add(newMethod);
                    }
                }
            }
            //初始化测试类方法和生产类方法
            for(Method m:methods){
                if(testSignature.contains(m.getSignature()))
                    testMethods.add(m);
                else
                    proMethods.add(m);
            }

            //获取dot文件，之后手动生成代码依赖图
            if(runType.equals("-c")){
                getDot(methods,getJarPath()+"\\class.dot","class");
                //生成pdf
//                String cmd = "dot -Tpdf F:\\软工二\\testSelection\\src\\main\\resources\\class.dot -o F:\\软工二\\testSelection\\src\\main\\resources\\class.pdf";
//                Runtime runtime = Runtime.getRuntime();
//                runtime.exec(cmd);

                //获取类级别测试用例选择
                getSelected_class(testMethods,proMethods,changeInfoDir);
            }
            else if(runType.equals("-m")){
                getDot(methods,getJarPath()+"\\method.dot","method");
                //生成pdf
//                String cmd = "dot -Tpdf F:\\软工二\\testSelection\\src\\main\\resources\\method.dot -o F:\\软工二\\testSelection\\src\\main\\resources\\method.pdf";
//                Runtime runtime = Runtime.getRuntime();
//                runtime.exec(cmd);

                //获取方法级别测试用例选择
                getSelected_method(testMethods,proMethods,changeInfoDir);
            }
            else{
                System.out.println("no such runType");
            }

        }catch (IOException e){
            e.printStackTrace();
        } catch (InvalidClassFileException e) {
            e.printStackTrace();
        } catch (ClassHierarchyException e) {
            e.printStackTrace();
        } catch (CancelException e) {
            e.printStackTrace();
        }
    }

    /**
     * @description:
     * @param dir :文件路径
     * @param classes :存取用的arraylist
     * @return: void
     */
    public static void getClasses(String dir,ArrayList<File> classes) {
        File file = new File(dir);
        File[] files=file.listFiles();
        if(files!=null) {
            for (File value : files) {
                if (value.isDirectory()) {
                    getClasses(value.getAbsolutePath(), classes);
                } else {
                    classes.add(value);
                }
            }
        }
    }

    /**
     * @description: 用于生成dot文件
     * @param methods：所有的方法
     * @param path：输出dot文件的路径
     * @param type：执行的类型（类级别或方法级别）
     */
    public static void getDot(ArrayList<Method> methods, String path, String type) {
        //用于标记哪些依赖已经被选取，避免重复
        ArrayList<String> exists = new ArrayList<String>();
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(path));
            if(type.equals("method"))
                out.write("digraph _method {\n");
            else if(type.equals("class"))
                out.write("digraph _class {\n");
            for (Method method : methods) {
                String signature = method.getSignature();
                String class_name = method.getClass_name();
                for (Method method1:method.getCalls()) {
                    String callSignature = method1.getSignature();
                    String callClass_name = method1.getClass_name();
                    if(type.equals("method")) {
                        if (callSignature.charAt(0) == signature.charAt(0)) {     //去掉原生类
                            String output = "\"" + callSignature + "\"" + " " + "->" + " " + "\"" + signature + "\"" + ";\n";
                            if (!exists.contains(output)) {
                                exists.add(output);
                                out.write(output);
                            }
                        }
                    }
                    else if(type.equals("class")){
                        if (callClass_name.charAt(1) == class_name.charAt(1)) {   //去掉原生类
                            String output = "\"" + callClass_name + "\"" + " " + "->" + " " + "\"" + class_name + "\"" + ";\n";
                            if(!exists.contains(output)) {
                                exists.add(output);
                                out.write(output);
                                System.out.println(output);
                            }
                        }
                    }
                }
            }
            out.write("}");
            out.close();
            System.out.println("创建dot成功");

            // 命令行输入  dot -Tpdf dot文件地址 -o 输出地址  以生产pdf文件

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @description: 获取类级别的测试用例选择信息
     * @param testMethods：所有的测试类方法
     * @param proMethods：所有的生产类方法
     * @param changeInfoDir：改变信息的地址
     */
    public static void getSelected_class(ArrayList<Method> testMethods,ArrayList<Method> proMethods,String changeInfoDir) {
        ArrayList<String> changeInfos = new ArrayList<String>();
        try {
            FileReader fileReader = new FileReader(changeInfoDir);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String str;
            // 按行读取字符串
            while ((str = bufferedReader.readLine()) != null && !str.equals("\n")) {
                changeInfos.add(str.split(" ")[0]);
            }
            bufferedReader.close();
            fileReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ArrayList<String> selected = new ArrayList<String>();
        for(String changeInfo:changeInfos) {
            for (Method testMethod : testMethods) {
                if(judge_Class(testMethod, changeInfo, proMethods, new ArrayList<String>())){
                    String s = testMethod.getClass_name() + " " + testMethod.getSignature();
                    if(!selected.contains(s)){
                        selected.add(s);
                    }
                }
            }
        }
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(getJarPath()+"\\selection-class.txt"));
            for (String i:selected) {
                out.write(i+'\n');
                //System.out.println(i);
            }
            out.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * @description: 判断target方法和改变的方法是否存在类级别的依赖关系
     * @param target：目标方法
     * @param changeInfo：改变的方法的信息（类名）
     * @param proMethods：生产类
     * @param visited：记录已经被选取过的类（为了避免类之间的循环依赖问题）
     * @return: boolean类型，返回true则存在依赖关系，反之则没有
     */
    public static boolean judge_Class(Method target,String changeInfo,ArrayList<Method> proMethods,ArrayList<String> visited){
        boolean flag = false;
        if (target.getClass_name().equals(changeInfo))
            return true;
        for(Method m:target.getCalls()){
            if(!visited.contains(m.getClass_name())) {
                visited.add(m.getClass_name());
                for (Method proMethod : proMethods) {
                    if (proMethod.getClass_name().equals(m.getClass_name()))
                        //进行递归遍历
                        flag = flag || judge_Class(proMethod, changeInfo, proMethods,visited);
                }
            }
        }
        return flag;
    }

    /**
     * @description: 获取方法级别的测试用例选择信息
     * @param testMethods：所有的测试类方法
     * @param proMethods：所有的生产类方法
     * @param changeInfoDir：改变信息的地址
     */
    public static void getSelected_method(ArrayList<Method> testMethods,ArrayList<Method> proMethods,String changeInfoDir) {
        ArrayList<String> changeInfos = new ArrayList<String>();
        try {
            FileReader fileReader = new FileReader(changeInfoDir);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String str;
            // 按行读取字符串
            while ((str = bufferedReader.readLine()) != null && !str.equals("\n")) {
                changeInfos.add(str.split(" ")[1]);
            }
            bufferedReader.close();
            fileReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        ArrayList<String> selected = new ArrayList<String>();
        for(String changeInfo: changeInfos) {
            for (Method testMethod : testMethods) {
                if (judge_Method(testMethod, changeInfo, proMethods)) {
                    String s = testMethod.getClass_name() + " " + testMethod.getSignature();
                    if (!selected.contains(s))
                        selected.add(s);
                }
            }
        }
        //写入文件
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(getJarPath()+"\\selection-method.txt"));
            for (String i:selected) {
                out.write(i+'\n');
                //System.out.println(i);
            }
            out.close();
        }catch (IOException e){
            e.printStackTrace();
        }

    }

    /**
     * @description: 判断target方法和改变的方法是否存在类级别的依赖关系
     * @param target：目标方法
     * @param changeInfo：改变的方法的信息（类名）
     * @param proMethods：生产类
     * @return: boolean类型，返回true则存在依赖关系，反之则没有
     */
    public static boolean judge_Method(Method target,String changeInfo,ArrayList<Method> proMethods){
        boolean flag = false;
        if (target.getSignature().equals(changeInfo))
            return true;
        for(Method m:target.getCalls()){
            for(Method proMethod:proMethods){
                if(proMethod.getSignature().equals(m.getSignature()))
                    flag = flag||judge_Method(proMethod,changeInfo,proMethods);
            }
        }
        return flag;
    }

    /**
     * @description: 得到jar包所在的路径
     * @return: string，jar包所在的路径
     */
    public static String getJarPath()
    {
        String path = TestSelection.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        try
        {
            path = java.net.URLDecoder.decode(path, "UTF-8"); // 转换处理中文及空格
            File file = new File(path);
            return file.getParent();
        }
        catch (java.io.UnsupportedEncodingException e)
        {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * @description: 用于初始化scope.txt和exclusion.txt文件
     */
    public static void init(){
        String exclusion = "apple\\/.*\n" +
                "com\\/apple\\/.*\n" +
                "com\\/ibm\\/.*\n" +
                "com\\/oracle\\/.*\n" +
                "com\\/sun\\/.*\n" +
                "dalvik\\/.*\n" +
                "java\\/beans\\/.*\n" +
                "java\\/io\\/ObjectStreamClass*\n" +
                "java\\/rmi\\/.*\n" +
                "java\\/text\\/.*\n" +
                "java\\/time\\/.*\n" +
                "javafx\\/.*\n" +
                "javafx\\/beans\\/.*\n" +
                "javafx\\/collections\\/.*\n" +
                "javafx\\/scene\\/.*\n" +
                "javax\\/accessibility\\/.*\n" +
                "javax\\/activation\\/.*\n" +
                "javax\\/activity\\/.*\n" +
                "javax\\/annotation\\/.*\n" +
                "javax\\/crypto\\/.*\n" +
                "javax\\/imageio\\/.*\n" +
                "javax\\/jnlp\\/.*\n" +
                "javax\\/jws\\/.*\n" +
                "javax\\/management\\/.*\n" +
                "javax\\/net\\/.*\n" +
                "javax\\/print\\/.*\n" +
                "javax\\/rmi\\/.*\n" +
                "javax\\/script\\/.*\n" +
                "javax\\/smartcardio\\/.*\n" +
                "javax\\/sound\\/.*\n" +
                "javax\\/sql\\/.*\n" +
                "javax\\/tools\\/.*\n" +
                "jdk\\/.*\n" +
                "netscape\\/.*\n" +
                "oracle\\/jrockit\\/.*\n" +
                "org\\/apache\\/xerces\\/.*\n" +
                "org\\/ietf\\/.*\n" +
                "org\\/jcp\\/.*\n" +
                "org\\/netbeans\\/.*\n" +
                "org\\/omg\\/.*\n" +
                "org\\/openide\\/.*\n" +
                "sun\\/.*\n" +
                "sun\\/awt\\/.*\n" +
                "sun\\/swing\\/.*\n";
        String scope = "Primordial,Java,stdlib,none";
        try {
            //创建scope.txt文件
            BufferedWriter out = new BufferedWriter(new FileWriter(getJarPath()+"\\scope.txt"));
            out.write(scope);
            out.close();
            //创建exclusion.txt文件
            out = new BufferedWriter(new FileWriter(getJarPath()+"\\exclusion.txt"));
            out.write(exclusion);
            out.close();
        }catch (IOException e){
            e.printStackTrace();
        }
    }
}
