package mvcframework.contoller;

import mvcframework.annotation.MyAutowired;
import mvcframework.annotation.MyController;
import mvcframework.annotation.MyRequestMapping;
import mvcframework.annotation.MyRequestParament;
import mvcframework.service.DemoService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 功能描述：（）
 *
 * @author: biubiubiu小浩
 * @date: 2019-01-03 21:35
 */
@MyController
@MyRequestMapping("/demo")
public class DemoController {
	@MyAutowired
	private DemoService demoService;

	@MyRequestMapping("/query")
	public void query(HttpServletRequest request, HttpServletResponse response, @MyRequestParament("name") String name) {
		String s = demoService.get(name);
		try {
			response.getWriter().write(s);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@MyRequestMapping("/add")
	public void add(HttpServletRequest request, HttpServletResponse response,@MyRequestParament("a") Integer a, @MyRequestParament("b") Integer b) {
		try {
			response.getWriter().write(a + "+" + b + "=" + (a + b));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@MyRequestMapping("/remove")
	public void remove(HttpServletRequest request,HttpServletResponse response,@MyRequestParament("id") Integer id){

	}
}