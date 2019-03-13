import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.vulkan.EXTDebugReport.VK_EXT_DEBUG_REPORT_EXTENSION_NAME;
import static org.lwjgl.vulkan.EXTDebugUtils.*;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.VK10.*;

class HelloTriangleApplication {

    private long window                             = VK_NULL_HANDLE;
    private long debugMessengerHandle               = VK_NULL_HANDLE;
    private long surfaceHandle                      = VK_NULL_HANDLE;

    private VkDevice vkLogicalDevice                = null;
    private VkInstance vkInstance                   = null;
    private VkPhysicalDevice physicalDevice         = null;

    private boolean enableValidationLayers          = true;

    private static List<String> validationLayers;

    static {
        validationLayers = new ArrayList<>(1);
        validationLayers.add("VK_LAYER_LUNARG_standard_validation");
    }

    void run(boolean enableValidationLayers) {
        this.enableValidationLayers = enableValidationLayers;
        initWindow();
        initVulkan();
        mainLoop();
        cleanup();
    }

    private void initWindow() {
        glfwInit();
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);

        final int WIDTH = 800;
        final int HEIGHT = 600;
        final String TITLE = "Vulkan";

        window = glfwCreateWindow(WIDTH, HEIGHT, TITLE, NULL, NULL);
    }

    private void initVulkan() {
        createInstance();
        setupDebugMessenger();
        createSurface();
        pickPhysicalDevice();
        createLogicalDevice();
    }

    private void mainLoop() {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
        }
    }

    private void cleanup() {
        vkDestroyDevice(vkLogicalDevice, null);

        if (enableValidationLayers) {
            destroyDebugUtilsMessengerExtension(vkInstance, debugMessengerHandle, null);
        }

        vkDestroySurfaceKHR(vkInstance, surfaceHandle, null);
        vkDestroyInstance(vkInstance, null);

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void createInstance() {
        if (enableValidationLayers && !checkValidationLayerSupport()) {
            throw new AssertionError("validation layers requested, but not available!");
        }

        try(MemoryStack stack = MemoryStack.stackPush()) {
            final ByteBuffer applicationName = memUTF8("Hello Triangle!");
            final ByteBuffer engineName = memUTF8("No engine!");

            VkApplicationInfo applicationInfo = VkApplicationInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(applicationName)
                .applicationVersion(VK_MAKE_VERSION(1, 0, 0))
                .pEngineName(engineName)
                .engineVersion(VK_MAKE_VERSION(1, 0, 0))
                .apiVersion(VK_API_VERSION_1_0);

            final PointerBuffer enabledExtensionNames = getRequiredExtensions();

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.callocStack(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(applicationInfo)
                .ppEnabledExtensionNames(enabledExtensionNames);

            logExtensionNames(createInfo);

            PointerBuffer pInstance = stack.mallocPointer(1);

            int vkResult = vkCreateInstance(createInfo, null, pInstance);

            if (vkResult != VK_SUCCESS) {
                throw new AssertionError("Failed to create vk instance!");
            }

            vkInstance = new VkInstance(pInstance.get(0), createInfo);

            ////////
            memFree(applicationName);
            memFree(engineName);
            memFree(enabledExtensionNames);
        }
    }

    private void setupDebugMessenger() {
        if (enableValidationLayers) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                VkDebugUtilsMessengerCreateInfoEXT createInfoEXT = VkDebugUtilsMessengerCreateInfoEXT.callocStack(stack)
                        .sType(VK_STRUCTURE_TYPE_DEBUG_UTILS_MESSENGER_CREATE_INFO_EXT)
                        .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_VERBOSE_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                        .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                        .pfnUserCallback(new VkDebugUtilsMessengerCallbackEXT() {
                            @Override
                            public int invoke(int messageSeverity, int messageTypes, long pCallbackData, long pUserData) {
                                System.err.println("validation layer: " + pCallbackData); // need a util class to translate these ints into messages

                                return VK_FALSE;
                            }
                        });

                if (createDebugUtilsMessengerExtension(vkInstance, createInfoEXT, null) != VK_SUCCESS) {
                    throw new AssertionError("Failed to set up debug messenger!");
                }
            }
        }
    }

    private int createDebugUtilsMessengerExtension(VkInstance instance, VkDebugUtilsMessengerCreateInfoEXT pCreateInfo, VkAllocationCallbacks pAllocator) {
        long functionHandle = vkGetInstanceProcAddr(instance, "vkCreateDebugUtilsMessengerEXT");
        if (functionHandle != NULL) {
            try(MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer pDebugMessenger = stack.mallocLong(1);
                int result = vkCreateDebugUtilsMessengerEXT(instance, pCreateInfo, pAllocator, pDebugMessenger);
                debugMessengerHandle = pDebugMessenger.get(0);

                return result;
            }
        }

        return VK_ERROR_EXTENSION_NOT_PRESENT;
    }

    private void destroyDebugUtilsMessengerExtension(VkInstance instance, long debugMessenger, VkAllocationCallbacks pAllocator) {
        long functionHandle = vkGetInstanceProcAddr(instance,"vkDestroyDebugUtilsMessengerEXT");
        if (functionHandle != NULL) {
            vkDestroyDebugUtilsMessengerEXT(instance, debugMessenger, pAllocator);
        }
    }

    private PointerBuffer getRequiredExtensions() {
        PointerBuffer requiredInstanceExtensions = glfwGetRequiredInstanceExtensions();

        if (requiredInstanceExtensions == null) {
            throw new AssertionError("Failed to find list of required vulkan extensions");
        }

        final ByteBuffer VK_EXT_DEBUG_REPORT_EXTENSION = memUTF8(VK_EXT_DEBUG_REPORT_EXTENSION_NAME);
        final ByteBuffer VK_EXT_DEBUG_UTILS_EXTENSION = memUTF8(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);

        PointerBuffer ppEnabledExtensionNames = memAllocPointer(requiredInstanceExtensions.remaining() + 2);
        ppEnabledExtensionNames.put(requiredInstanceExtensions);
        ppEnabledExtensionNames.put(VK_EXT_DEBUG_REPORT_EXTENSION);
        ppEnabledExtensionNames.put(VK_EXT_DEBUG_UTILS_EXTENSION);
        ppEnabledExtensionNames.flip();

        //////
        memFree(VK_EXT_DEBUG_REPORT_EXTENSION);
        memFree(VK_EXT_DEBUG_UTILS_EXTENSION);

        return ppEnabledExtensionNames;
    }

    private boolean checkValidationLayerSupport() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer layerCount = stack.mallocInt(1);
            vkEnumerateInstanceLayerProperties(layerCount, null);

            VkLayerProperties.Buffer availableValidationLayers = VkLayerProperties.mallocStack(layerCount.get(0), stack);
            vkEnumerateInstanceLayerProperties(layerCount, availableValidationLayers);

            for (String validationLayer : validationLayers) {
                boolean layerFound = false;

                while (availableValidationLayers.hasRemaining()) {
                    if (validationLayer.equals(availableValidationLayers.get().layerNameString())) {
                        layerFound = true;
                        break;
                    }
                }

                if (!layerFound) {
                    return false;
                }
            }

            return true;
        }
    }

    private void logExtensionNames(VkInstanceCreateInfo createInfo) {
        // see extensions names
        PointerBuffer e =  createInfo.ppEnabledExtensionNames();

        if (e == null) {
            throw new AssertionError("No extension names found!");
        }

        while (e.hasRemaining()){
            System.out.println(e.getStringUTF8());
        }
    }

    private void pickPhysicalDevice() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer deviceCount = stack.mallocInt(1);
            vkEnumeratePhysicalDevices(vkInstance, deviceCount, null);

            if (deviceCount.get(0) == NULL) {
                throw new AssertionError("Failed to find GPUs with Vulkan support!");
            }

            PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
            vkEnumeratePhysicalDevices(vkInstance, deviceCount, devices);

            while (devices.hasRemaining()) {
                VkPhysicalDevice physicalDevice = new VkPhysicalDevice(devices.get(), vkInstance);
                if (findQueueFamilies(physicalDevice).isComplete()) {
                    this.physicalDevice = physicalDevice;
                    break;
                }
            }

            if (this.physicalDevice == null) {
                throw new AssertionError("Failed to find a suitable GPU!");
            }
        }
    }

    private void createLogicalDevice() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            QueueFamilyIndices indices = findQueueFamilies(this.physicalDevice);

            VkPhysicalDeviceFeatures vkPhysicalDeviceFeatures = VkPhysicalDeviceFeatures.callocStack(stack);

            VkDeviceCreateInfo vkDeviceCreateInfo = VkDeviceCreateInfo.callocStack(stack);
            vkDeviceCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO);

            VkDeviceQueueCreateInfo.Buffer queueCreateInfo = VkDeviceQueueCreateInfo.callocStack(1, stack);
            queueCreateInfo.sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO);
            queueCreateInfo.queueFamilyIndex(indices.getIndex());

            final float queuePriority = 1.0f;
            FloatBuffer pQueuePriority = stack.mallocFloat(1);
            pQueuePriority.put(queuePriority);
            pQueuePriority.flip();

            queueCreateInfo.pQueuePriorities(pQueuePriority);

            vkDeviceCreateInfo.pQueueCreateInfos(queueCreateInfo);
            vkDeviceCreateInfo.pEnabledFeatures(vkPhysicalDeviceFeatures);

            if (enableValidationLayers) {
                ByteBuffer validationLayer = memUTF8(validationLayers.get(0));

                PointerBuffer pValidationLayers = stack.mallocPointer(1);
                pValidationLayers.put(validationLayer);
                pValidationLayers.flip();

                vkDeviceCreateInfo.ppEnabledLayerNames(pValidationLayers);

                memFree(validationLayer);
            }

            PointerBuffer pGraphicsQueue = stack.mallocPointer(1);

            if (vkCreateDevice(this.physicalDevice, vkDeviceCreateInfo, null, pGraphicsQueue) != VK_SUCCESS) {
                throw new AssertionError("Failed to create a logical device!");
            }

            vkLogicalDevice = new VkDevice(pGraphicsQueue.get(0), physicalDevice,vkDeviceCreateInfo);

            vkGetDeviceQueue(vkLogicalDevice, indices.getIndex(), 0, pGraphicsQueue);
        }
    }

    private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice physicalDevice) {
        QueueFamilyIndices queueFamilyIndices = new QueueFamilyIndices();

        try(MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pQueueFamilyCount = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyCount, null);

            VkQueueFamilyProperties.Buffer queueFamilyProperties = VkQueueFamilyProperties.mallocStack(pQueueFamilyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pQueueFamilyCount, queueFamilyProperties);

            int i=0;
            for (VkQueueFamilyProperties queueFamilyProperty : queueFamilyProperties) {
                if (queueFamilyProperty.queueCount() > 0 && (queueFamilyProperty.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    queueFamilyIndices.setIndex(i);
                    break;
                }

                i++;
            }

            return queueFamilyIndices;
        }
    }
    private void createSurface() {
        try(MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);

            if (glfwCreateWindowSurface(vkInstance, window, null, pSurface) != VK_SUCCESS) {
                throw new AssertionError("Failed to create window surface! ");
            }

            surfaceHandle = pSurface.get(0);
        }
    }
}
