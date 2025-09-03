package com.example.bpstatistics.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * BrightPattern 구독 요청 (agent_grids 기반)
 * curl 예시의 JSON 구조 매핑
 * {
 *   "1": { "agent_grids": [ { ...grid... } ] }
 * }
 */
public class BrightPatternSubscriptionRequest {

    @NotEmpty
    private List<AgentGrid> agent_grids; // key "agent_grids"

    // 내부 Grid 정의
    public static class AgentGrid {
        @Size(max = 50)
        private String id;               // "1"
        private List<String> team_ids;   // team UUID 리스트
        private Integer limit;           // 1000
        private List<Column> columns;    // 통계 컬럼 정의
        private List<Order> order;       // 정렬
        private List<String> service_ids;// 빈 배열 가능
        private Boolean my_subteam_only;
        private Boolean logged_in_agents_only;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public List<String> getTeam_ids() { return team_ids; }
        public void setTeam_ids(List<String> team_ids) { this.team_ids = team_ids; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
        public List<Column> getColumns() { return columns; }
        public void setColumns(List<Column> columns) { this.columns = columns; }
        public List<Order> getOrder() { return order; }
        public void setOrder(List<Order> order) { this.order = order; }
        public List<String> getService_ids() { return service_ids; }
        public void setService_ids(List<String> service_ids) { this.service_ids = service_ids; }
        public Boolean getMy_subteam_only() { return my_subteam_only; }
        public void setMy_subteam_only(Boolean my_subteam_only) { this.my_subteam_only = my_subteam_only; }
        public Boolean getLogged_in_agents_only() { return logged_in_agents_only; }
        public void setLogged_in_agents_only(Boolean logged_in_agents_only) { this.logged_in_agents_only = logged_in_agents_only; }
    }

    public static class Column {
        private String id;       // "1" .. "10"
        private String statName; // first_last_name 등
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getStatName() { return statName; }
        public void setStatName(String statName) { this.statName = statName; }
    }

    public static class Order {
        private String by;   // "1"
        private String dir;  // DESC/ASC
        public String getBy() { return by; }
        public void setBy(String by) { this.by = by; }
        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
    }

    public List<AgentGrid> getAgent_grids() { return agent_grids; }
    public void setAgent_grids(List<AgentGrid> agent_grids) { this.agent_grids = agent_grids; }
}
