/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.plugin.datasource.impl.oracle;

import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.datasource.constants.ContextConstant;
import com.alibaba.nacos.plugin.datasource.constants.DataSourceConstant;
import com.alibaba.nacos.plugin.datasource.constants.FieldConstant;
import com.alibaba.nacos.plugin.datasource.constants.TableConstant;
import com.alibaba.nacos.plugin.datasource.mapper.AbstractMapper;
import com.alibaba.nacos.plugin.datasource.mapper.ConfigInfoMapper;
import com.alibaba.nacos.plugin.datasource.model.MapperContext;
import com.alibaba.nacos.plugin.datasource.model.MapperResult;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The mysql implementation of ConfigInfoMapper.
 *
 * @author hyx
 **/

public class ConfigInfoMapperByOracle extends AbstractMapper implements ConfigInfoMapper {

	private static final String DATA_ID = "dataId";

	private static final String GROUP = "group";

	private static final String APP_NAME = "appName";

	private static final String CONTENT = "content";

	private static final String TENANT = "tenant";

	@Override
	public MapperResult findConfigInfoByAppFetchRows(MapperContext context) {
		final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
		final String tenantId = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
		String sql = "SELECT * FROM (SELECT id,data_id,group_id,tenant_id,app_name,content, ROWNUM as rnum FROM config_info"
				+ " WHERE tenant_id LIKE ? AND app_name= ?)" + " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum";
		return new MapperResult(sql, CollectionUtils.list(tenantId, appName));
	}

	@Override
	public MapperResult getTenantIdList(MapperContext context) {
		String sql = "SELECT * FROM (SELECT tenant_id, ROWNUM as rnum FROM config_info WHERE tenant_id != '' GROUP BY tenant_id)"
				+ " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " +(context.getPageSize() + context.getStartRow()) + " >= rnum";
		return new MapperResult(sql, Collections.emptyList());
	}

	@Override
	public MapperResult getGroupIdList(MapperContext context) {
		String sql = "SELECT * FROM (SELECT group_id, ROWNUM as rnum FROM config_info WHERE tenant_id ='" + NamespaceUtil.getNamespaceDefaultId()
				+"' GROUP BY group_id) " + " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum";
		return new MapperResult(sql, Collections.emptyList());
	}

	@Override
	public MapperResult findAllConfigKey(MapperContext context) {
		String sql = " SELECT data_id,group_id,app_name  FROM ( "
				+ " SELECT * FROM (SELECT id, ROWNUM as rnum FROM config_info WHERE tenant_id LIKE ? ORDER BY id)"
				+ " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum"
				+ " )" + " g, config_info t WHERE g.id = t.id  ";
		return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.TENANT_ID)));
	}

	@Override
	public MapperResult findAllConfigInfoBaseFetchRows(MapperContext context) {
		String sql = "SELECT t.id,data_id,group_id,content,md5"
				+ " FROM (SELECT * FROM ( SELECT id, ROWNUM as rnum FROM config_info ORDER BY id)"
				+ " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum) "
				+ " g, config_info t  WHERE g.id = t.id ";
		return new MapperResult(sql, Collections.emptyList());
	}

	@Override
	public MapperResult findAllConfigInfoFragment(MapperContext context) {
		String contextParameter = context.getContextParameter(ContextConstant.NEED_CONTENT);
		boolean needContent = contextParameter != null && Boolean.parseBoolean(contextParameter);
		String sql = "SELECT * FROM (SELECT id,data_id,group_id,tenant_id,app_name," + (needContent ? "content," : "")
				+ "md5,gmt_modified,type,encrypted_data_key "
				+ ", ROWNUM as rnum FROM config_info WHERE id > ? ORDER BY id ASC) " + " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum ";
		return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.ID)));
	}


	@Override
	public MapperResult findChangeConfig(MapperContext context) {
		String sql ="SELECT data_id, group_id, tenant_id, app_name, content, gmt_modified,encrypted_data_key "
				+ "FROM config_info WHERE gmt_modified >= ? AND gmt_modified <= ?";
		return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.START_TIME),
				context.getWhereParameter(FieldConstant.LAST_MAX_ID)));
	}

	@Override
	public MapperResult findChangeConfigFetchRows(MapperContext context) {
		final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
		final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
		final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
		final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
		final String tenantTmp = StringUtils.isBlank(tenant) ? StringUtils.EMPTY : tenant;
		final Timestamp startTime = (Timestamp) context.getWhereParameter(FieldConstant.START_TIME);
		final Timestamp endTime = (Timestamp) context.getWhereParameter(FieldConstant.END_TIME);

		List<Object> paramList = new ArrayList<>();

		final String sqlFetchRows = "SELECT * FROM (SELECT id,data_id,group_id,tenant_id,app_name,content,type,md5,gmt_modified"
				+ ", ROWNUM as rnum FROM config_info WHERE ";
		String where = " 1=1 ";
		if (!StringUtils.isBlank(dataId)) {
			where += " AND data_id LIKE ? ";
			paramList.add(dataId);
		}
		if (!StringUtils.isBlank(group)) {
			where += " AND group_id LIKE ? ";
			paramList.add(group);
		}

		if (!StringUtils.isBlank(tenantTmp)) {
			where += " AND tenant_id = ? ";
			paramList.add(tenantTmp);
		}

		if (!StringUtils.isBlank(appName)) {
			where += " AND app_name = ? ";
			paramList.add(appName);
		}
		if (startTime != null) {
			where += " AND gmt_modified >=? ";
			paramList.add(startTime);
		}
		if (endTime != null) {
			where += " AND gmt_modified <=? ";
			paramList.add(endTime);
		}
		return new MapperResult(
				sqlFetchRows + where + " AND id > " + context.getWhereParameter(FieldConstant.LAST_MAX_ID) +
				" ORDER BY id ASC)" + " WHERE  rnum >= " + 0 + " and " + context.getPageSize() + " >= rnum ", paramList);
	}

	@Override
	public MapperResult listGroupKeyMd5ByPageFetchRows(MapperContext context) {
		String sql = "SELECT t.id,data_id,group_id,tenant_id,app_name,md5,type,gmt_modified,encrypted_data_key FROM "
				+ "(SELECT * FROM ( SELECT id, ROWNUM as rnum FROM config_info ORDER BY id) "
				+ " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum "
				+ " ) g, config_info t WHERE g.id = t.id";
		return new MapperResult(sql, Collections.emptyList());
	}

	@Override
	public MapperResult findConfigInfoBaseLikeFetchRows(MapperContext context) {
		final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
		final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
		final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);

		final String sqlFetchRows = "SELECT * FROM (SELECT id,data_id,group_id,tenant_id,content, ROWNUM as rnum FROM config_info WHERE ";
		String where = " 1=1 AND tenant_id='" + NamespaceUtil.getNamespaceDefaultId() + "' ";

		List<Object> paramList = new ArrayList<>();

		if (!StringUtils.isBlank(dataId)) {
			where += " AND data_id LIKE ? ";
			paramList.add(dataId);
		}
		if (!StringUtils.isBlank(group)) {
			where += " AND group_id LIKE ";
			paramList.add(group);
		}
		if (!StringUtils.isBlank(content)) {
			where += " AND content LIKE ? ";
			paramList.add(content);
		}
		return new MapperResult(sqlFetchRows + where + ") " + " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum ",
				paramList);
	}

	@Override
	public MapperResult findConfigInfo4PageFetchRows(MapperContext context) {
		final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
		final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
		final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
		final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
		final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);

		List<Object> paramList = new ArrayList<>();

		final String sql = "SELECT * FROM (SELECT id,data_id,group_id,tenant_id,app_name,content,type,encrypted_data_key,"
				+ " ROWNUM as rnum FROM config_info";
		StringBuilder where = new StringBuilder(" WHERE ");
		where.append(" tenant_id=? ");
		paramList.add(tenant);
		if (StringUtils.isNotBlank(dataId)) {
			where.append(" AND data_id=? ");
			paramList.add(dataId);
		}
		if (StringUtils.isNotBlank(group)) {
			where.append(" AND group_id=? ");
			paramList.add(group);
		}
		if (StringUtils.isNotBlank(appName)) {
			where.append(" AND app_name=? ");
			paramList.add(appName);
		}
		if (!StringUtils.isBlank(content)) {
			where.append(" AND content LIKE ? ");
			paramList.add(content);
		}
		return new MapperResult(sql + where + ")  " + " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum ",
				paramList);
	}


		@Override
		public MapperResult findConfigInfoBaseByGroupFetchRows(MapperContext context) {
			String sql = "SELECT * FROM (SELECT id,data_id,group_id,content, ROWNUM as rnum FROM config_info WHERE group_id=? AND tenant_id=?" + ")  "
				+ " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum ";
			return new MapperResult(sql, CollectionUtils.list(context.getWhereParameter(FieldConstant.GROUP_ID),
					context.getWhereParameter(FieldConstant.TENANT_ID)));
	}

	@Override
	public MapperResult findConfigInfoLike4PageFetchRows(MapperContext context) {
		final String tenant = (String) context.getWhereParameter(FieldConstant.TENANT_ID);
		final String dataId = (String) context.getWhereParameter(FieldConstant.DATA_ID);
		final String group = (String) context.getWhereParameter(FieldConstant.GROUP_ID);
		final String appName = (String) context.getWhereParameter(FieldConstant.APP_NAME);
		final String content = (String) context.getWhereParameter(FieldConstant.CONTENT);

		List<Object> paramList = new ArrayList<>();

		final String sqlFetchRows = "SELECT * FROM (SELECT id,data_id,group_id,tenant_id,app_name,content,encrypted_data_key,"
				+ " ROWNUM as rnum FROM config_info";
		StringBuilder where = new StringBuilder(" WHERE ");
		where.append(" tenant_id LIKE ? ");
		paramList.add(tenant);

		if (!StringUtils.isBlank(dataId)) {
			where.append(" AND data_id LIKE ? ");
			paramList.add(dataId);

		}
		if (!StringUtils.isBlank(group)) {
			where.append(" AND group_id LIKE ? ");
			paramList.add(group);
		}
		if (!StringUtils.isBlank(appName)) {
			where.append(" AND app_name = ? ");
			paramList.add(appName);
		}
		if (!StringUtils.isBlank(content)) {
			where.append(" AND content LIKE ? ");
			paramList.add(content);
		}
		return new MapperResult(sqlFetchRows + where + " ) " + " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum ",
				paramList);
	}

	@Override
	public MapperResult findAllConfigInfoFetchRows(MapperContext context) {
		String sql = "SELECT t.id,data_id,group_id,tenant_id,app_name,content,md5 "
				+ " FROM (SELECT * FROM (  SELECT id, ROWNUM as rnum FROM config_info WHERE tenant_id LIKE ? ORDER BY id)"
				+ " WHERE  rnum >= " + (context.getStartRow() + 1) + " and " + (context.getPageSize() + context.getStartRow()) + " >= rnum)"
				+ " g, config_info t  WHERE g.id = t.id ";
		return new MapperResult(sql,
				CollectionUtils.list(context.getWhereParameter(FieldConstant.TENANT_ID)));
	}

	@Override
	public String getDataSource() {
		return DataSourceConstant.ORACLE;
	}
}
