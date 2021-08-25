/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.ui.controller;

import javax.servlet.http.HttpServletRequest;

import org.apache.iotdb.ui.config.shiro.UsernamePasswordIdToken;
import org.apache.iotdb.ui.entity.Connect;
import org.apache.iotdb.ui.entity.User;
import org.apache.iotdb.ui.mapper.ConnectDao;
import org.apache.iotdb.ui.mapper.UserDao;
import org.apache.iotdb.ui.model.BaseVO;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@CrossOrigin
@RestController
@Api(value = "登录相关接口")
public class UserController {

	public static final String USER = "USER";

	@Autowired
	private UserDao userDao;

	@Autowired
	private ConnectDao connectDao;

	@ApiOperation(value = "errorTest", notes = "test")
	@GetMapping(value = "/error/{errorName}")
	public String error(HttpServletRequest request, @PathVariable("errorName") String errorName) {
		return "error:" + errorName;
	}

	@ApiOperation(value = "user", notes = "user")
	@GetMapping(value = "/user")
	public BaseVO<User> user(@RequestParam("id") Long id) {
		User user = userDao.select(id);
		return BaseVO.success(user);
	}

	@ApiOperation(value = "connect", notes = "connect")
	@GetMapping(value = "/connect")
	public BaseVO<Connect> connect(@RequestParam("id") Long id) {
		Connect connect = connectDao.select(id);
		return BaseVO.success(connect);
	}

	@ApiOperation(value = "/api/login/account", notes = "/api/login/account")
	@RequestMapping(value = "/api/login/account", method = { RequestMethod.GET, RequestMethod.POST })
	public BaseVO<JSONObject> loginAccount(@RequestParam("username") String username,
			@RequestParam("password") String password) {
		JSONObject json = new JSONObject();
		if (username == null) {
			json.put("status", "error");
			json.put("type", "account");
			json.put("currentAuthority", "guest");
			return new BaseVO<JSONObject>("1", "用户名不能为空", json);
		}
		User u = new User();
		u.setName(username);
		User user = userDao.selectOne(u);
		BaseVO<JSONObject> ret = null;
		Subject subject = SecurityUtils.getSubject();
		UsernamePasswordIdToken token = new UsernamePasswordIdToken(username, password, String.valueOf(user.getId()),
				user.getPassword());
		user.setPassword(null);
		try {
			subject.login(token);
			Session session = subject.getSession();
			if (session != null) {
				session.setAttribute(USER, user);
			}
			json.put("status", "ok");
			json.put("type", "account");
			json.put("currentAuthority", "admin");
			ret = BaseVO.success(json);
		} catch (Exception e) {
			json.put("status", "error");
			json.put("type", "account");
			json.put("currentAuthority", "guest");
			ret = new BaseVO<JSONObject>("1", "找不到用户或用户名与密码不匹配", json);
		}
		return ret;
	}

	@ApiOperation(value = "/api/currentUser", notes = "/api/currentUser")
	@RequestMapping(value = "/api/currentUser", method = { RequestMethod.GET, RequestMethod.POST })
	public BaseVO<JSONObject> currentUser() {
		Subject subject = SecurityUtils.getSubject();
		User user = (User) subject.getSession().getAttribute(USER);
		JSONObject json = new JSONObject();
		json.put("name", user.getName());
		json.put("avatar", "https://gw.alipayobjects.com/zos/rmsportal/BiazfanxmamNRoxxVxka.png");
		json.put("userid", "00000001");
		json.put("email", "limengag@yonyou.com");
		json.put("signature", "海纳百川，有容乃大");
		json.put("title", "交互专家");
		json.put("group", "蚂蚁金服－某某某事业群－某某平台部－某某技术部－UED");
		json.put("tags", new JSONArray());
		json.getJSONArray("tags").add(JSONObject.parseObject("{\"key\":\"0\",\"label\":\"很有想法的\"}"));
		json.getJSONArray("tags").add(JSONObject.parseObject("{\"key\":\"1\",\"label\":\"专注设计\"}"));
		json.getJSONArray("tags").add(JSONObject.parseObject("{\"key\":\"2\",\"label\":\"辣~\"}"));
		json.getJSONArray("tags").add(JSONObject.parseObject("{\"key\":\"3\",\"label\":\"大长腿\"}"));
		json.getJSONArray("tags").add(JSONObject.parseObject("{\"key\":\"4\",\"label\":\"川妹子\"}"));
		json.getJSONArray("tags").add(JSONObject.parseObject("{\"key\":\"5\",\"label\":\"海纳百川\"}"));
		json.put("notifyCount", 12);
		json.put("unreadCount", 11);
		json.put("country", "China");
		json.put("geographic", new JSONObject());
		json.getJSONObject("geographic").put("province",
				JSONObject.parseObject("{\"key\":\"330000\",\"label\":\"浙江省\"}"));
		json.getJSONObject("geographic").put("city", JSONObject.parseObject("{\"key\":\"330100\",\"label\":\"杭州市\"}"));
		json.put("address", "西湖区工专路 77 号");
		json.put("phone", "0752-268888888");
		return BaseVO.success(json);
	}

	@ApiOperation(value = "/api/outLogin", notes = "/api/outLogin")
	@RequestMapping(value = "/api/outLogin", method = { RequestMethod.GET, RequestMethod.POST })
	public JSONObject outLogin() {
		Subject subject = SecurityUtils.getSubject();
		subject.logout();
		JSONObject ret = new JSONObject();
		ret.put("data", new JSONObject());
		ret.put("success", true);
		return ret;
	}

	// 简单的无返回值的handler，无需写入swagger
	@RequestMapping(value = "/toLogin", method = { RequestMethod.GET, RequestMethod.POST })
	public void toLogin() {
	}
}
