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

import org.apache.iotdb.ui.entity.Connect;
import org.apache.iotdb.ui.entity.User;
import org.apache.iotdb.ui.mapper.ConnectDao;
import org.apache.iotdb.ui.mapper.UserDao;
import org.apache.iotdb.ui.model.BaseVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@Api(value = "登录相关接口")
public class UserController {

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
	public BaseVO<JSONObject> loginAccount() {
		JSONObject json = new JSONObject();
		json.put("status", "ok");
		json.put("type", "account");
		json.put("currentAuthority", "admin");
		BaseVO<JSONObject> ret = BaseVO.success(json);
		return ret;
	}
}
