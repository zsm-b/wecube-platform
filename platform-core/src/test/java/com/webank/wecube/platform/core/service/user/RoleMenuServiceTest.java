package com.webank.wecube.platform.core.service.user;

import static com.webank.wecube.platform.core.domain.plugin.PluginPackage.Status.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.google.common.collect.Lists;
import com.webank.wecube.platform.core.DatabaseBasedTest;
import com.webank.wecube.platform.core.commons.ApplicationProperties;
import com.webank.wecube.platform.core.domain.MenuItem;
import com.webank.wecube.platform.core.domain.RoleMenu;
import com.webank.wecube.platform.core.domain.plugin.PluginPackage;
import com.webank.wecube.platform.core.domain.plugin.PluginPackageMenu;
import com.webank.wecube.platform.core.dto.user.RoleMenuDto;
import com.webank.wecube.platform.core.jpa.MenuItemRepository;
import com.webank.wecube.platform.core.jpa.PluginPackageRepository;
import com.webank.wecube.platform.core.jpa.user.RoleMenuRepository;

@Ignore
public class RoleMenuServiceTest extends DatabaseBasedTest {

    static final String ROLE_ONE = "1";
    static final String ROLE_ONE_NAME = "1";
    static final String ROLE_TWO = "2";
    static final String ROLE_TWO_NAME = "2";
    static final String TOKEN = "Bearer";
    @Autowired
    private RoleMenuRepository roleMenuRepository;
    @Autowired
    private RoleMenuServiceImpl roleMenuService;
    @Autowired
    private MenuItemRepository menuItemRepository;
    @Autowired
    private PluginPackageRepository pluginPackageRepository;
    @Autowired
    @Qualifier(value = "userJwtSsoTokenRestTemplate")
    private RestTemplate restTemplate;
    @Autowired
    private ApplicationProperties applicationProperties;
    private String gatewayUrl;
    private MockRestServiceServer server;

    @Before
    public void setUp() {
        mockSysMenus();
        mockPackageMenus();
        mockRoleMenuData();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        gatewayUrl = this.applicationProperties.getGatewayUrl();
    }

    @Test
    public void retrieveMenusByRoleIdShouldSucceed() {
        RoleMenuDto roleMenuDtoFromRoleOne = this.roleMenuService.retrieveMenusByRoleId(ROLE_ONE);
        assertThat(roleMenuDtoFromRoleOne.getMenuList().size()).isEqualTo(2);
        assertThat(roleMenuDtoFromRoleOne.getMenuList().get(0).getDisplayName()).isEqualTo("CI Integrated Enquiry");
        assertThat(roleMenuDtoFromRoleOne.getMenuList().get(1).getDisplayName()).isEqualTo("Enum Enquiry");


        RoleMenuDto roleMenuDtoFromRoleTwo = this.roleMenuService.retrieveMenusByRoleId(ROLE_TWO);
        assertThat(roleMenuDtoFromRoleTwo.getMenuList().size()).isEqualTo(2);
        assertThat(roleMenuDtoFromRoleTwo.getMenuList().get(0).getDisplayName()).isEqualTo("TEST_MENU_ONE");
        assertThat(roleMenuDtoFromRoleTwo.getMenuList().get(1).getDisplayName()).isEqualTo("TEST_MENU_TWO");

    }

    @Test
    public void updateMenusByRoleIdWithPackageMenusShouldSucceed() {
        mockRetrieveRoleOneInfoServer();
        ArrayList<String> menuCodeList = Lists.newArrayList(
                "DESIGNING_CI_INTEGRATED_QUERY_EXECUTION",
                "CMDB_DESIGNING_ENUM_ENQUIRY",
                "CMDB_DESIGNING_TEST_MENU_ONE",
                "CMDB_DESIGNING_TEST_MENU_TWO");
        ArrayList<String> menuDisplayNameList = Lists.newArrayList("CI Integrated Enquiry", "Enum Enquiry", "TEST_MENU_ONE", "TEST_MENU_TWO");
        this.roleMenuService.updateRoleToMenusByRoleId(TOKEN, ROLE_ONE, menuCodeList);
        RoleMenuDto roleMenuDtoFromRoleOne = this.roleMenuService.retrieveMenusByRoleId(ROLE_ONE);
        assertThat(roleMenuDtoFromRoleOne.getMenuList().size()).isEqualTo(menuCodeList.size());
        for (int i = 0; i < menuCodeList.size(); i++) {
            System.out.println(roleMenuDtoFromRoleOne.getMenuList().get(i).getMenuOrder());
            assertThat(roleMenuDtoFromRoleOne.getMenuList().get(i).getDisplayName()).isEqualTo(menuDisplayNameList.get(i));
        }
        server.verify();
    }

    @Test
    public void updateMenusByRoleIdWithSystemMenusShouldSucceed() {
        mockRetrieveRoleOneInfoServer();
        ArrayList<String> menuCodeList = Lists.newArrayList(
                "DESIGNING",
                "OPERATING");
        ArrayList<String> menuDisplayNameList = Lists.newArrayList("designing display", "operating display");
        this.roleMenuService.updateRoleToMenusByRoleId(TOKEN, ROLE_ONE, menuCodeList);
        RoleMenuDto roleMenuDtoFromRoleOne = this.roleMenuService.retrieveMenusByRoleId(ROLE_ONE);
        assertThat(roleMenuDtoFromRoleOne.getMenuList().size()).isEqualTo(menuCodeList.size());
        for (int i = 0; i < menuCodeList.size(); i++) {
            assertThat(roleMenuDtoFromRoleOne.getMenuList().get(i).getDisplayName()).isEqualTo(menuDisplayNameList.get(i));
        }
        server.verify();
    }

    @Test
    public void updateMenusByRoleIdWithMixedTypesOfMenusShouldSucceed() {
        mockRetrieveRoleTwoInfoServer();
        ArrayList<String> menuCodeList = Lists.newArrayList(
                "DESIGNING_CI_INTEGRATED_QUERY_EXECUTION",
                "CMDB_DESIGNING_TEST_MENU_ONE",
                "DESIGNING");
        ArrayList<String> menuDisplayNameList = Lists.newArrayList("designing display", "CI Integrated Enquiry", "TEST_MENU_ONE");
        this.roleMenuService.updateRoleToMenusByRoleId(TOKEN, ROLE_TWO, menuCodeList);
        RoleMenuDto roleMenuDtoFromRoleTwo = this.roleMenuService.retrieveMenusByRoleId(ROLE_TWO);
        assertThat(roleMenuDtoFromRoleTwo.getMenuList().size()).isEqualTo(menuCodeList.size());
        for (int i = 0; i < menuCodeList.size(); i++) {
            assertThat(roleMenuDtoFromRoleTwo.getMenuList().get(i).getDisplayName()).isEqualTo(menuDisplayNameList.get(i));
        }
        server.verify();
    }

    private void mockRoleMenuData() {
        List<RoleMenu> roleMenuList = new ArrayList<>();
        roleMenuList.add(new RoleMenu(ROLE_ONE, ROLE_ONE_NAME, "DESIGNING_CI_INTEGRATED_QUERY_EXECUTION"));
        roleMenuList.add(new RoleMenu(ROLE_ONE, ROLE_ONE_NAME, "CMDB_DESIGNING_ENUM_ENQUIRY"));
        roleMenuList.add(new RoleMenu(ROLE_TWO, ROLE_TWO_NAME, "CMDB_DESIGNING_TEST_MENU_ONE"));
        roleMenuList.add(new RoleMenu(ROLE_TWO, ROLE_TWO_NAME, "CMDB_DESIGNING_TEST_MENU_TWO"));
        this.roleMenuRepository.saveAll(roleMenuList);
    }

    private void mockPackageMenus() {
        long now = System.currentTimeMillis();
        PluginPackage runningPluginPackage = new PluginPackage(null, "wecmdb", "v0.3", RUNNING, new Timestamp(now + 20000), false);


        PluginPackageMenu packageMenuForRunning1 = new PluginPackageMenu(runningPluginPackage, "DESIGNING_CI_INTEGRATED_QUERY_EXECUTION", "DESIGNING", "CI Integrated Enquiry", "/wecmdb/designing/ci-integrated-query-execution");
        PluginPackageMenu packageMenuForRunning2 = new PluginPackageMenu(runningPluginPackage, "CMDB_DESIGNING_ENUM_ENQUIRY", "DESIGNING", "Enum Enquiry", "/wecmdb/designing/enum-enquiry");
        PluginPackageMenu packageMenuForRunning3 = new PluginPackageMenu(runningPluginPackage, "CMDB_DESIGNING_TEST_MENU_ONE", "OPERATING", "TEST_MENU_ONE", "/wecmdb/designing/enum-enquiry");
        PluginPackageMenu packageMenuForRunning4 = new PluginPackageMenu(runningPluginPackage, "CMDB_DESIGNING_TEST_MENU_TWO", "OPERATING", "TEST_MENU_TWO", "/wecmdb/designing/enum-enquiry");

        Set<PluginPackageMenu> packageMenuSet = new LinkedHashSet<>();
        packageMenuSet.add(packageMenuForRunning1);
        packageMenuSet.add(packageMenuForRunning2);
        packageMenuSet.add(packageMenuForRunning3);
        packageMenuSet.add(packageMenuForRunning4);
        runningPluginPackage.setPluginPackageMenus(packageMenuSet);
        pluginPackageRepository.saveAll(Lists.newArrayList(runningPluginPackage));
    }

    private void mockSysMenus() {
        MenuItem menuItem_ONE = new MenuItem("DESIGNING", null, "designing display");
        MenuItem menuItem_TWO = new MenuItem("OPERATING", null, "operating display");
        menuItemRepository.saveAll(Lists.newArrayList(menuItem_ONE, menuItem_TWO));
    }


    private void mockRetrieveRoleOneInfoServer() {
        server.expect(ExpectedCount.manyTimes(), requestTo(String.format("http://%s/auth/v1/roles/%s", this.gatewayUrl, ROLE_ONE)))
                .andExpect(method(HttpMethod.GET))
//                .andExpect(header("Authorization", TOKEN))
                .andRespond(withSuccess("{\n" +
                        "    \"status\": \"OK\",\n" +
                        "    \"message\": \"Success\",\n" +
                        "    \"data\": {\n" +
                        "        \"id\": 1,\n" +
                        "        \"name\": \"1\",\n" +
                        "        \"displayName\": \"1_display_name\"\n" +
                        "    }\n" +
                        "}", MediaType.APPLICATION_JSON));
    }

    private void mockRetrieveRoleTwoInfoServer() {
        server.expect(ExpectedCount.manyTimes(), requestTo(String.format("http://%s/auth/v1/roles/%s", this.gatewayUrl, ROLE_TWO)))
                .andExpect(method(HttpMethod.GET))
//                .andExpect(header("Authorization", TOKEN))
                .andRespond(withSuccess("{\n" +
                        "    \"status\": \"OK\",\n" +
                        "    \"message\": \"Success\",\n" +
                        "    \"data\": {\n" +
                        "        \"id\": 2,\n" +
                        "        \"name\": \"2\",\n" +
                        "        \"displayName\": \"2_display_name\"\n" +
                        "    }\n" +
                        "}", MediaType.APPLICATION_JSON));
    }


}
