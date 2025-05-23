
package com.example.projektkalkulationeksamen.service;


import com.example.projektkalkulationeksamen.dto.MilestoneDTO;
import com.example.projektkalkulationeksamen.dto.ProjectDTO;
import com.example.projektkalkulationeksamen.dto.TaskDTO;
import com.example.projektkalkulationeksamen.exceptions.database.DatabaseException;
import com.example.projektkalkulationeksamen.exceptions.database.DeletionException;
import com.example.projektkalkulationeksamen.exceptions.project.ProjectCreationException;
import com.example.projektkalkulationeksamen.exceptions.notfound.ProjectNotFoundException;
import com.example.projektkalkulationeksamen.exceptions.project.ProjectUpdateException;
import com.example.projektkalkulationeksamen.model.Project;
import com.example.projektkalkulationeksamen.model.Status;
import com.example.projektkalkulationeksamen.repository.ProjectRepository;
import com.example.projektkalkulationeksamen.validation.ProjectDataValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProjectService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final MilestoneService milestoneService;

    @Autowired
    public ProjectService(ProjectRepository projectRepository, MilestoneService milestoneService) {
        this.projectRepository = projectRepository;
        this.milestoneService = milestoneService;
    }


    public List<Project> getAllProjects() {
        logger.debug("Retrieving all projects");
        return projectRepository.getAllProjects();
    }

    public Project getProjectById(int id) {
        logger.debug("Sends project with ID: {}", id);

        return projectRepository.findProjectById(id)
                .orElseThrow(() -> {
                    logger.warn("Project not found with ID: {}", id);
                    return new ProjectNotFoundException("Failed to get project by ID: " + id);
                });
    }

    public void addProject(Project newProject) {
        logger.debug("Attempting to create new project with project name: {}", newProject.getProjectName());

        if (projectExistsByName(newProject.getProjectName())) {
            logger.warn("Project name: {} already taken", newProject.getProjectName());
            throw new ProjectCreationException("Project name already taken");
        }

        ProjectDataValidator.validateName(newProject.getProjectName());
        ProjectDataValidator.validateDescription(newProject.getDescription());

        try {
            projectRepository.addProject(newProject);
        } catch (DatabaseException e) {
            logger.error("Failed to create project with project name: {}", newProject.getProjectName(), e);
            throw new ProjectCreationException("Failed to create project with project name: " + newProject.getProjectName(), e);
        }
    }

    public void deleteProject(int id) {
        logger.debug("Attempting to delete project with ID: {}", id);

        try {
            boolean deleted = projectRepository.deleteProject(id);

            if (!deleted) {
                logger.warn("Failed to delete project with ID: {}", id);
                throw new ProjectNotFoundException("Failed to delete project with ID: " + id);
            }
        } catch (DatabaseException e) {
            logger.error("Failed to delete project with ID: {}", id, e);
            throw new DeletionException("Failed to delete project with ID: " + id, e);
        }
    }

    public void updateProject(Project updatedProject) {
        logger.debug("Attempting to update project with ID: {}", updatedProject.getId());

        try {
            Project currentProject = projectRepository.findProjectById(updatedProject.getId())
                    .orElseThrow(() -> new ProjectNotFoundException("Project not found with id: " + updatedProject.getId()));

            ProjectDataValidator.validateName(updatedProject.getProjectName());
            ProjectDataValidator.validateDescription(updatedProject.getDescription());

            if (!currentProject.getProjectName().equalsIgnoreCase(updatedProject.getProjectName()) && otherProjectExistsWithName(currentProject.getId(), updatedProject.getProjectName())) {
                throw new ProjectUpdateException("Project name already taken");
            }

            // When changing the project status to "COMPLETED", update completed_at to the current LocalDateTime.
            if (updatedProject.getStatus().equals(Status.COMPLETED) && currentProject.getCompletedAt() == null) {
                updatedProject.setCompletedAt(LocalDateTime.now());

                // If changed back from "COMPLETED" to "In progress" or "Not started" remove the completed_at value.
            } else if (updatedProject.getStatus() != Status.COMPLETED) {
                updatedProject.setCompletedAt(null);
            }
            boolean updated = projectRepository.updateProject(updatedProject);

            if (!updated) {
                logger.warn("Cannot update. No project found with ID: {}", updatedProject.getId());
                throw new ProjectUpdateException("Cannot update. No project found with ID: " + updatedProject.getId());
            }

            logger.info("Succesfully updated project with ID: {}", updatedProject.getId());
        } catch (DatabaseException e) {
            logger.error("Database error while trying to update project with ID: {}", updatedProject.getId(), e);
            throw new ProjectUpdateException("Database error while updating project with ID: " + updatedProject.getId(), e);
        }
    }

    public int getProjectProgress(int projectId) {
        List<MilestoneDTO> milestonesWithDetails = milestoneService.getMilestonesByProjectIdWithDetails(projectId);

        if (milestonesWithDetails.isEmpty()) {
            return 0;
        }

        int finishedMilestones = 0;

        for (MilestoneDTO milestoneDTO : milestonesWithDetails) {
            if (milestoneDTO.getMilestoneStatus() == Status.COMPLETED) {
                finishedMilestones++;
            }
        }

        return (int) Math.round((finishedMilestones * 100.0) / milestonesWithDetails.size());

    }

    // returns true if a project name already exists
    public boolean projectExistsByName(String name) {
        return projectRepository.findProjectByName(name).isPresent();
    }

    // Ensures no duplicate names (ignoring its own name when updating)
    public boolean otherProjectExistsWithName(int currentProjectId, String name) {
        Optional<Project> existing = projectRepository.findProjectByName(name);
        return existing.isPresent() && existing.get().getId() != currentProjectId;
    }


    public List<MilestoneDTO> getFinishedMileStonesFromProject(int projectId) {

        ProjectDTO projectDTO = getProjectWithDetails(projectId);

        List<MilestoneDTO> allProjectMileStonesWithDetails = projectDTO.getMilestones();

        List<MilestoneDTO> finishedMileStones = new ArrayList<>();

        for (MilestoneDTO milestoneDTO : allProjectMileStonesWithDetails) {
            if (milestoneDTO.getMilestoneStatus() == Status.COMPLETED) {
                finishedMileStones.add(milestoneDTO);
            }
        }
        return finishedMileStones;
    }

    public List<MilestoneDTO> getOngoingMileStonesFromProject(int projectId) {

        ProjectDTO projectDTO = getProjectWithDetails(projectId);

        List<MilestoneDTO> allProjectMileStonesWithDetails = projectDTO.getMilestones();

        List<MilestoneDTO> ongoingMileStones = new ArrayList<>();

        for (MilestoneDTO milestoneDTO : allProjectMileStonesWithDetails) {
            if (milestoneDTO.getMilestoneStatus() != Status.COMPLETED) {
                ongoingMileStones.add(milestoneDTO);
            }
        }
        return ongoingMileStones;
    }

    public ProjectDTO getProjectWithDetails(int id) {
        Project project = getProjectById(id);
        List<MilestoneDTO> milestonesByProjectIdWithDetails = milestoneService.getMilestonesByProjectIdWithDetails(id);
        int progress = getProjectProgress(id);
        return new ProjectDTO(
                project.getId(),
                project.getProjectName(),
                project.getDescription(),
                project.getCreatedAt(),
                project.getProjectManagerId(),
                project.getStatus(),
                project.getDeadline(),
                project.getStartDate(),
                project.getCompletedAt(),
                milestonesByProjectIdWithDetails,
                progress
        );
    }

    public List<ProjectDTO> getAllProjectsWithDetails() {
        List<ProjectDTO> allProjectsWithDetails = new ArrayList<>();
        List<Project> allProjects = getAllProjects();

        for (Project project : allProjects) {
            allProjectsWithDetails.add(getProjectWithDetails(project.getId()));
        }

        return allProjectsWithDetails;
    }
// returns all projects that has the status of "COMPLETED"
    public List<ProjectDTO> getAllFinishedProjectsWithDetails() {
        List<ProjectDTO> allProjects = getAllProjectsWithDetails();
        List<ProjectDTO> finishedProjects = new ArrayList<>();

        for (ProjectDTO projectDTO : allProjects) {
            if (projectDTO.getStatus() == Status.COMPLETED) {
                finishedProjects.add(projectDTO);
            }
        }
        return finishedProjects;
    }
// returns all projects that has the status of "IN PROGRESS" or "NOT STARTED"
    public List<ProjectDTO> getAllOngoingProjects() {
        List<ProjectDTO> allProjects = getAllProjectsWithDetails();
        List<ProjectDTO> ongoingProjects = new ArrayList<>();

        for (ProjectDTO projectDTO : allProjects) {
            if (projectDTO.getStatus() != Status.COMPLETED) {
                ongoingProjects.add(projectDTO);
            }
        }

        return ongoingProjects;
    }

    public List<ProjectDTO> getAllProjectDTOsByProjectManagerId(int projectManagerId) {
        List<ProjectDTO> allProjects = getAllProjectsWithDetails();
        List<ProjectDTO> selectedProjects = new ArrayList<>();

        for (ProjectDTO projectDTO : allProjects) {
            if (projectDTO.getProjectManagerId() == projectManagerId) {
                selectedProjects.add(projectDTO);
            }
        }
        return selectedProjects;
    }

    public List<ProjectDTO> getAllProjectsByEmployeeId(int employeeId) {
        List<ProjectDTO> allProjects = getAllProjectsWithDetails();
        Set<Integer> foundProjectIds = new HashSet<>();

        for (ProjectDTO projectDTO : allProjects) {
            for (MilestoneDTO milestoneDTO : projectDTO.getMilestones()) {
                List<TaskDTO> tasks = milestoneDTO.getTasks();

                for (TaskDTO taskDTO : tasks) {
                    List<Integer> coworkerIds = taskDTO.getCoworkerIds();

                    for (Integer integer : coworkerIds) {
                        if (integer == employeeId) {
                            foundProjectIds.add(projectDTO.getId());
                        }
                    }
                }
            }
        }

        List<ProjectDTO> foundProjects = new ArrayList<>();

        for (Integer integer : foundProjectIds) {
            foundProjects.add(getProjectWithDetails(integer));
        }
        return foundProjects;
    }

    public int estimatedHours(int projectId) {
        int estimatedHours = 0;
        ProjectDTO projectDTO = getProjectWithDetails(projectId);
        for (MilestoneDTO milestoneDTO : projectDTO.getMilestones()) {
            for (TaskDTO taskDTO : milestoneDTO.getTasks()) {
                estimatedHours += taskDTO.getEstimatedHours();
            }
        }
        return estimatedHours;
    }

    public int actualHoursUsed(int projectId) {
        int actualHoursUsed = 0;
        ProjectDTO projectDTO = getProjectWithDetails(projectId);
        for (MilestoneDTO milestoneDTO : projectDTO.getMilestones()) {
            for (TaskDTO taskDTO : milestoneDTO.getTasks()) {
                actualHoursUsed += taskDTO.getActualHoursUsed();
            }
        }
        return actualHoursUsed;
    }

}
