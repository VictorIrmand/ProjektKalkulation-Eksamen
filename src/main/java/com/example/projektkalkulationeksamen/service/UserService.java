package com.example.projektkalkulationeksamen.service;

import com.example.projektkalkulationeksamen.exceptions.database.DatabaseException;
import com.example.projektkalkulationeksamen.exceptions.database.DeletionException;
import com.example.projektkalkulationeksamen.exceptions.user.UserCreationException;
import com.example.projektkalkulationeksamen.exceptions.notfound.UserNotFoundException;
import com.example.projektkalkulationeksamen.exceptions.user.UserUpdateException;
import com.example.projektkalkulationeksamen.model.Role;
import com.example.projektkalkulationeksamen.model.User;
import com.example.projektkalkulationeksamen.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        logger.debug("Sends list of all users");
        return userRepository.getAllUsers();
    }

    public List<User> getAllEmployees() {
        logger.debug("Sends list of all employees");
        List<User> employees = new ArrayList<>();

        for (User user : getAllUsers()) {
            if (user.getRole() == Role.EMPLOYEE) {
                employees.add(user);
            }
        }
        return employees;
    }

    public User getUserById(int id) {
        logger.debug("Sends user with ID: {}", id);

        Optional<User> foundUser = userRepository.getUserById(id);

        return foundUser.orElseThrow(() -> {
            logger.warn("User not found with ID: {}", id);
            return new UserNotFoundException("Failed to find user with ID: " + id);
        });
    }

    public User getUserByUsername(String username) {
        logger.debug("Attempting to send user with username: {}", username);
        Optional<User> foundUser = userRepository.getUserByUsername(username);

        return foundUser.orElseThrow(() -> {
            logger.warn("User not found with username: {}", username);
            return new UserNotFoundException("Failed to find user by username: " + username);
        });
    }

    public User addUser(User user) {
        logger.debug("Attempting to add user with username: {}", user.getUsername());
        try {
            User createdUser = userRepository.addUser(user);
            logger.info("Succesfully added user with username: {}", user.getUsername());
            return createdUser;
        } catch (DatabaseException e) {
            logger.error("Failed to add user to database with username: {}", user.getUsername(), e);
            throw new UserCreationException("Failed to create user with username: " + user.getUsername(), e);
        }
    }

    public void deleteUser(int id) {
        logger.debug("Attempting to delete user with id {}", id);

        try {
            boolean deleted = userRepository.deleteUser(id);

            if (!deleted) {
                logger.warn("Failed to delete user with ID: {}", id);
                throw new UserNotFoundException("Failed to delete user with ID " + id);
            }
            logger.info("Successfully deleted user with ID: {}", id);
        } catch (DatabaseException e) {
            logger.error("Failed to delete user with ID: {}", id, e);
            throw new DeletionException("Failed to delete user with ID " + id, e);
        }
    }

    public void updateUser(User user) {
        logger.debug("Attempting to update user with ID: {}", user.getId());

        try {
            boolean updated = userRepository.updateUser(user);

            if (!updated) {
                logger.warn("No user found to update with ID {}", user.getId());
                throw new UserNotFoundException("No user found to update with ID: " + user.getId());
            }

            logger.info("Successfully updated user with ID {}", user.getId());

        } catch (DatabaseException e) {
            logger.error("Database error while updating user with ID {}", user.getId(), e);
            throw new UserUpdateException("Database error while updating user with ID: " + user.getId(), e);
        }
    }

    public boolean userExistsByUsernameExcludeId(String username, int excludedId) {
        Optional<User> user = userRepository.getUserByUsername(username);

        return user.isPresent() && user.get().getId() != excludedId;
    }

    public boolean userExistsByUsername(String username) {
        return userRepository.getUserByUsername(username).isPresent();
    }

    public boolean userExistsById(int id) {
        return userRepository.getUserById(id).isPresent();
    }

}

