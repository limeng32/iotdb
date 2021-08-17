--
-- Licensed to the Apache Software Foundation (ASF) under one
-- or more contributor license agreements.  See the NOTICE file
-- distributed with this work for additional information
-- regarding copyright ownership.  The ASF licenses this file
-- to you under the Apache License, Version 2.0 (the
-- "License"); you may not use this file except in compliance
-- with the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the License is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
-- KIND, either express or implied.  See the License for the
-- specific language governing permissions and limitations
-- under the License.
--

drop table if exists tb_connect;

drop table if exists tb_query;

drop table if exists tb_user;

create table tb_connect
(
   id                   bigint ,
   host                 varchar(128) ,
   port                 integer ,
   username             varchar(20) ,
   password             varchar(20) ,
   alias                varchar(100) ,
   user_id              bigint 
);

create table tb_query
(
   id                   bigint ,
   name                 varchar(100) ,
   sqls                 varchar(10000) ,
   connect_id           bigint  
);

create table tb_user
(
   id                   bigint ,
   name                 varchar(20) ,
   password             varchar(200) 
);