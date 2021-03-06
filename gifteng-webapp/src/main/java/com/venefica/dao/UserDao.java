package com.venefica.dao;

import com.venefica.model.User;
import java.util.List;

/**
 * Data access interface for {@link User} entity.
 *
 * @author Sviatoslav Grebenchukov
 */
public interface UserDao {

    /**
     * Get the user by it's id.
     *
     * @param id id of the user
     * @return user object
     */
    public User get(Long id);
    
    /**
     * 
     * @param userId
     * @return 
     */
    public User getEager(Long userId);

    /**
     * 
     * @return the list of available users
     */
    public List<User> getAll();
    
    /**
     * 
     * @return the list of admin users
     */
    public List<User> getAdminUsers();
    
    /**
     * Returns a list of top users having the highest giving number.
     * 
     * @param numberUsers
     * @return 
     */
    public List<User> getTopUsers(int numberUsers);
    
    /**
     * 
     * @param userId
     * @return list of user followers
     */
    public List<User> getFollowers(Long userId);
    
    /**
     * Finds the user by his name.
     *
     * @param name name of the user
     * @return user object
     */
    public User findUserByName(String name);

    /**
     * Finds the user by his email.
     *
     * @param email email of the user
     * @return user object
     */
    public User findUserByEmail(String email);

    /**
     * Finds the user by his phone number.
     *
     * @param phoneNumber phone number of the user
     * @return user object
     */
    public User findUserByPhoneNumber(String phoneNumber);

    /**
     * Stores the user in the database.
     *
     * @param user user object to store
     * @return user id
     */
    public Long save(User user);

    /**
     * Updates the user.
     *
     * @param user updated user object
     */
    public void update(User user);

    /**
     * Deletes the user by his name.
     *
     * @param name the name of the user to delete
     * @return true if the user has been successfully deleted
     */
    public boolean removeByName(String name);

    /**
     * Deletes the user by his email address
     *
     * @param email the email of the user to delete
     * @return true if the user has been successfully deleted
     */
    public boolean removeByEmail(String email);

    /**
     * Returns max user id stored in the database.
     *
     * @return max user id
     */
    public Long getMaxUserId();
}
