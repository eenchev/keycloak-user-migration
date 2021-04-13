package com.danielfrak.code.keycloak.providers.rest.remote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.danielfrak.code.keycloak.providers.rest.ConfigurationProperties.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserModelFactoryTest {

    private static final String MODEL_ID = "modelId";

    private UserModelFactory userModelFactory;

    private MultivaluedHashMap<String, String> config;

    @Mock
    private KeycloakSession session;
    @Mock
    private ComponentModel model;

    @BeforeEach
    void setUp() {
        config = new MultivaluedHashMap<>();
        config.put(ROLE_MAP_PROPERTY, List.of("oldRole:newRole"));
        config.put(GROUP_MAP_PROPERTY, List.of("oldGroup:newGroup"));

        when(model.getConfig())
                .thenReturn(config);
        userModelFactory = new UserModelFactory(session, model);

        lenient().when(model.getId())
                .thenReturn(MODEL_ID);
    }

    @Test
    void createsUser() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        var result = userModelFactory.create(legacyUser, realm);

        assertNotNull(result);
    }

    private LegacyUser createLegacyUser(String username) {
        return createLegacyUser(username, null);
    }

    private LegacyUser createLegacyUser(String username, String id) {
        var legacyUser = new LegacyUser();
        legacyUser.setId(id);
        legacyUser.setUsername(username);
        legacyUser.setEmail("user@email.com");
        legacyUser.setEmailVerified(true);
        legacyUser.setEnabled(true);
        legacyUser.setFirstName("John");
        legacyUser.setLastName("Smith");
        return legacyUser;
    }

    @Test
    void throwsExceptionIfLegacyAndKeycloakUsernamesNotEqual() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel("wrong_username"));

        LegacyUser legacyUser = createLegacyUser(username);
        assertThrows(IllegalStateException.class, () -> userModelFactory.create(legacyUser, realm));
    }

    @Test
    void migratesBasicAttributes() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(legacyUser.getUsername(), result.getUsername());
        assertEquals(legacyUser.getEmail(), result.getEmail());
        assertEquals(legacyUser.isEmailVerified(), result.isEmailVerified());
        assertEquals(legacyUser.isEnabled(), result.isEnabled());
        assertEquals(legacyUser.getFirstName(), result.getFirstName());
        assertEquals(legacyUser.getLastName(), result.getLastName());
    }

    @Test
    void migratesLegacyUserId() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";
        final String id = "legacy-user-id";
        final LegacyUser legacyUser = createLegacyUser(username, id);
        final TestUserModel testUserModel = new TestUserModel(username, id);

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, legacyUser.getId(), username, true, false))
                .thenReturn(testUserModel);

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(legacyUser.getId(), result.getId());
    }

    @Test
    void migratesRolesWithoutUnmapped() {
        config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "false");
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";
        final RoleModel newRoleModel = mock(RoleModel.class);

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));
        when(realm.getRole("newRole"))
                .thenReturn(newRoleModel);

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setRoles(List.of("oldRole", "anotherRole"));

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(Set.of(newRoleModel), result.getRoleMappings());
    }

    @Test
    void migratesGroupsWithoutUnmapped() {
        config.putSingle(MIGRATE_UNMAPPED_GROUPS_PROPERTY, "false");
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";
        final GroupModel newGroupModel = mock(GroupModel.class);

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));
        when(newGroupModel.getName()).thenReturn("newGroup");
        when(realm.getGroups()).thenReturn(Collections.singletonList(newGroupModel));

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setGroups(List.of("oldGroup", "anotherGroup"));

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(Set.of(newGroupModel), result.getGroups());
    }

    @Test
    void migratesUnmappedRoles() {
        config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "true");
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";
        final RoleModel newRoleModel = mock(RoleModel.class);
        final RoleModel anotherRoleModel = mock(RoleModel.class);

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));
        when(realm.getRole("newRole"))
                .thenReturn(newRoleModel);
        when(realm.getRole("anotherRole"))
                .thenReturn(anotherRoleModel);

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setRoles(List.of("oldRole", "anotherRole"));

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(Set.of(newRoleModel, anotherRoleModel), result.getRoleMappings());
    }

    @Test
    void migratesUnmappedGroups() {
        config.putSingle(MIGRATE_UNMAPPED_GROUPS_PROPERTY, "true");
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";
        final GroupModel newGroupModel = mock(GroupModel.class);
        final GroupModel anotherGroupModel = mock(GroupModel.class);

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));
        when(realm.getGroups()).thenReturn(Collections.emptyList());
        GroupModel parentGroup = null;
        when(realm.createGroup("newGroup", parentGroup)).thenReturn(newGroupModel);
        when(realm.createGroup("anotherGroup", parentGroup)).thenReturn(anotherGroupModel);

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setGroups(List.of("newGroup", "anotherGroup"));

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(Set.of(newGroupModel, anotherGroupModel), result.getGroups());
    }

    @Test
    void addUserToExistingGroup() {
        config.putSingle(MIGRATE_UNMAPPED_GROUPS_PROPERTY, "true");

        final String username = "user";
        final String groupName = "group";
        final String groupId = "12345";

        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final GroupModel newGroupModel = mock(GroupModel.class);

        when(session.userLocalStorage()).thenReturn(userProvider);
        when(userProvider.addUser(realm, username)).thenReturn(new TestUserModel(username));
        when(realm.getGroups()).thenReturn(List.of(newGroupModel));
        when(newGroupModel.getName()).thenReturn(groupName);
        when(newGroupModel.getId()).thenReturn(groupId);

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setGroups(List.of(groupName));

        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(Set.of(newGroupModel), result.getGroups());
    }

    @Test
    void migrateUserWithNullGroups() {
        config.putSingle(MIGRATE_UNMAPPED_GROUPS_PROPERTY, "true");
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage()).thenReturn(userProvider);
        when(userProvider.addUser(realm, username)).thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        List<String> groups = new ArrayList<>();
        groups.add(null);
        groups.add("");
        legacyUser.setGroups(groups);

        var result = userModelFactory.create(legacyUser, realm);

        assertTrue(result.getGroups().isEmpty());
    }

    @Test
    void migratesUserWithNullRoles() {
        config.putSingle(MIGRATE_UNMAPPED_ROLES_PROPERTY, "true");
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage()).thenReturn(userProvider);
        when(userProvider.addUser(realm, username)).thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        List<String> roles = new ArrayList<>();
        roles.add(null);
        roles.add("");
        legacyUser.setRoles(roles);

        var result = userModelFactory.create(legacyUser, realm);

        assertTrue(result.getRoleMappings().isEmpty());
    }

    @Test
    void migratesAttribute() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        legacyUser.setAttributes(Map.of("someAttribute", List.of("someValue")));
        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(legacyUser.getAttributes(), result.getAttributes());
    }

    @Test
    void setsFederationLink() {
        final UserProvider userProvider = mock(UserProvider.class);
        final RealmModel realm = mock(RealmModel.class);
        final String username = "user";

        when(session.userLocalStorage())
                .thenReturn(userProvider);
        when(userProvider.addUser(realm, username))
                .thenReturn(new TestUserModel(username));

        LegacyUser legacyUser = createLegacyUser(username);
        var result = userModelFactory.create(legacyUser, realm);

        assertEquals(MODEL_ID, result.getFederationLink());
    }
}
