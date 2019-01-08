package mvcframework.servlet;

import mvcframework.annotation.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 功能描述：（）
 *
 * @author: biubiubiu小浩
 * @date: 2019-01-03 21:14
 */
public class MyDispatherServlet extends HttpServlet {
	private Properties contextConfig = new Properties();

	private List<String> classNames = new ArrayList<>();

	private Map<String, Object> ioc = new HashMap<>(16);

//	private Map<String, Method> handleMapping = new HashMap<>(16);

	private List<Handler> handleMapping = new ArrayList<>();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		this.doPost(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		//6、等待请求阶段
//		String url = req.getRequestURI();
//		String contextPath = req.getContextPath();
//		url = url.replace(contextPath, "").replaceAll("/+", "/");
//		if (!handleMapping.containsKey(url)) {
//			resp.getWriter().write("404 Not Found!!!");
//			return;
//		}
//		Method method = handleMapping.get(url);
//		System.out.println(method);
		try {
			doDispatch(req,resp);
		} catch (Exception e) {
			resp.getWriter().write("500 Exception,Details:\r\n"+Arrays.toString(e.getStackTrace()));
		}

	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		//1、加载配置文件
		doLoadConfig(config.getInitParameter("contextConfigLocation"));

		//2、扫描所有相关联的包
		doScanner(contextConfig.getProperty("scanPackage"));

		//3、初始化所有相关的类，并且将其保存到IOC容器中
		doInstance();

		//4、执行依赖注入（把加了@MyAutowired注解的字段赋值）
		doAutowired();

		//5、构造HandlerMapping,将URL和Method进行关联
		initHandlerMapping();


		System.out.println("MySpringMVC  is init!!!");
	}

	private void initHandlerMapping() {
		if (ioc.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> entry : ioc.entrySet()) {
			Class<?> aClass = entry.getValue().getClass();
			if (!aClass.isAnnotationPresent(MyController.class)) {
				continue;
			}

			String baseUrl = "";

			if (aClass.isAnnotationPresent(MyRequestMapping.class)) {
				MyRequestMapping requestMapping = aClass.getAnnotation(MyRequestMapping.class);
				baseUrl = requestMapping.value();
			}

			Method[] methods = aClass.getMethods();
			for (Method method : methods) {
				if (!method.isAnnotationPresent(MyRequestMapping.class)) {
					continue;
				}
				MyRequestMapping annotation = method.getAnnotation(MyRequestMapping.class);
				String url = annotation.value();
				url = (baseUrl + url).replaceAll("/+", "/");
//				handleMapping.put(url, method);
				Pattern pattern = Pattern.compile(url);
				handleMapping.add(new Handler(entry.getValue(),method,pattern));
				System.out.println("Mapping : " + url + "," + method);
			}
		}

	}

	private void doAutowired() {
		if (ioc.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> entry : ioc.entrySet()) {
			// 注入就是把所有的ioc容器中的字段加了@MyAutowired注解的字段全部复制
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for (Field field : fields) {
				// 判断是否加了注解
				if (!field.isAnnotationPresent(MyAutowired.class)) {
					continue;
				}
				MyAutowired annotation = field.getAnnotation(MyAutowired.class);
				String beanName = annotation.value().trim();
				if ("".equals(beanName)) {
					beanName = field.getType().getName();
				}

				// 设置强制访问
				field.setAccessible(true);

				try {
					field.set(entry.getValue(), ioc.get(beanName));
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void doInstance() {
		if (classNames.isEmpty()) {
			return;
		}
		try {
 			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);

				//IOC容器中key的规则
				//1、默认类名首字母小写
				//2、自定义命名 优先使用自定义命名
				//3、自动类型匹配，例如将实现类赋值给接口

 				if (clazz.isAnnotationPresent(MyController.class)) {
					Object instance = clazz.newInstance();
					String beanName = lowerFirestCase(clazz.getSimpleName());
					ioc.put(beanName, instance);
				} else if (clazz.isAnnotationPresent(MyService.class)) {
					MyService annotation = clazz.getAnnotation(MyService.class);
					String beanName = annotation.value();
					if ("".equals(beanName)) {
						beanName = lowerFirestCase(clazz.getSimpleName());
					}
					Object instance = clazz.newInstance();
					ioc.put(beanName, instance);

					//3、自动类型匹配，例如将实现类赋值给接口
					Class<?>[] interfaces = clazz.getInterfaces();
					for (Class<?> aClass : interfaces) {
						ioc.put(aClass.getName(), instance);
					}
				} else {
					continue;
				}
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		}
	}

	private String lowerFirestCase(String simpleName) {
		char[] chars = simpleName.toCharArray();
		chars[0] += 32;
		return String.valueOf(chars);
	}

	private void doScanner(String basePackage) {
		URL url = this.getClass().getClassLoader().getResource("/" + basePackage.replaceAll("\\.", "/"));
		File dir = new File(url.getFile());
		for (File file : dir.listFiles()) {
			if (file.isDirectory()) {
				doScanner(basePackage + "." + file.getName());
			} else {
				String className = basePackage + "." + file.getName().replace(".class", "");
				classNames.add(className);
				System.out.println(className);
			}
		}
	}

	private void doLoadConfig(String contextConfigLocation) {
		InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);

		try {
			contextConfig.load(is);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void doDispatch(HttpServletRequest request,HttpServletResponse response){

		try {
			Handler handler = getHandler(request);
			if (handler == null){
				response.getWriter().write("404 Not Found!");
				return;
			}

			//获取所有方法的参数列表
			Class<?>[] parameterTypes = handler.method.getParameterTypes();

			//保存所有需要自动赋值的参数值
			Object[] paramterValues = new Object[parameterTypes.length];

			Map<String, String[]> parameterMap = request.getParameterMap();

			for (Map.Entry<String,String[]> param : parameterMap.entrySet()){
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","").replaceAll(",\\s","");

				//如果找到匹配的对象，则开始填充参数值
				if (!handler.paramIndexMapping.containsKey(param.getKey())){return;}
				int index = handler.paramIndexMapping.get(param.getKey());
				paramterValues[index] = covert(parameterTypes[index],value);
			}

			//设置方法中的request和response对象
			Integer reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramterValues[reqIndex] = request;
			Integer resIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramterValues[resIndex] = response;

			handler.method.invoke(handler.controller,paramterValues);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private Handler getHandler(HttpServletRequest request) throws Exception{
		if (handleMapping.isEmpty()){
			return null;
		}

		String url = request.getRequestURI();
		String contextPath = request.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");
		for (Handler handler : handleMapping) {
			try {
				Matcher matcher = handler.pattern.matcher(url);
				if (!matcher.matches()){
					continue;
				}

				return handler;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private Object covert(Class<?> type,String value){
		if (Integer.class == type){
			return Integer.valueOf(value);
		}
		return value;
	}

	private class Handler {
		/**
		 * 保存方法对应的实例
		 */
		protected Object controller;
		/**
		 * 保存映射的方法
		 */
		protected Method method;
		/**
		 *
		 */
		protected Pattern pattern;
		/**
		 * 参数顺序
		 */
		protected Map<String, Integer> paramIndexMapping;

		protected Handler(Object controller, Method method, Pattern pattern) {
			this.controller = controller;
			this.method = method;
			this.pattern = pattern;

			paramIndexMapping = new HashMap<>();
			pubtIndexMapping(method);
		}

		private void pubtIndexMapping(Method method) {
			// 提取方法中加了注解的参数
			Annotation[][] parameterAnnotations = method.getParameterAnnotations();
			for (int i = 0; i < parameterAnnotations.length; i++) {
				for (Annotation annotation : parameterAnnotations[i]) {
					if (annotation instanceof MyRequestParament) {
						String paramName = ((MyRequestParament) annotation).value();
						if (!"".equals(paramName.trim())) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}

			// 提取方法中的request和response参数
			Class<?>[] parameterTypes = method.getParameterTypes();
			for (int i = 0; i < parameterTypes.length; i++) {
				Class<?> parameterType = parameterTypes[i];
				if (parameterType == HttpServletRequest.class ||
						parameterType == HttpServletResponse.class) {
					paramIndexMapping.put(parameterType.getName(), i);
				}
			}
		}
	}
}