import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import api, {
  adminService,
  systemService,
  authService,
  trackingService,
  shipmentService,
  agentService,
} from '../api';
import adminServiceDefault, { adminService as adminServiceNamed } from '../adminService';

describe('adminService & Frontend Admin API Integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  describe('API Service Endpoints', () => {
    it('getOverview() calls GET /admin/overview', async () => {
      const mockOverview = {
        success: true,
        message: 'Admin dashboard overview retrieved successfully',
        data: {
          totalShipments: 12,
          unassignedShipments: 3,
          assignedShipments: 4,
          inTransitShipments: 2,
          outForDeliveryShipments: 1,
          deliveredShipments: 2,
          failedDeliveryShipments: 0,
          activeAgents: 5,
        },
      };

      const getSpy = vi.spyOn(api, 'get').mockResolvedValue(mockOverview);

      const response = await adminService.getOverview();

      expect(getSpy).toHaveBeenCalledWith('/admin/overview');
      expect(response).toEqual(mockOverview);
    });

    it('getShipments() calls GET /admin/shipments with pagination params', async () => {
      const mockPage = {
        success: true,
        message: 'Shipments retrieved successfully',
        data: {
          content: [
            { id: 1, trackingNumber: 'TRK-ADMIN-0001', status: 'CREATED' },
            { id: 2, trackingNumber: 'TRK-ADMIN-0002', status: 'ASSIGNED' },
          ],
          totalElements: 2,
          totalPages: 1,
          number: 0,
          size: 10,
        },
      };

      const getSpy = vi.spyOn(api, 'get').mockResolvedValue(mockPage);

      const params = { page: 0, size: 10, sort: 'createdAt,desc' };
      const response = await adminService.getShipments(params);

      expect(getSpy).toHaveBeenCalledWith('/admin/shipments', { params });
      expect(response.data.content).toHaveLength(2);
      expect(response.data.totalElements).toBe(2);
    });

    it('getShipments() calls GET /admin/shipments with combined filters', async () => {
      const mockFiltered = {
        success: true,
        message: 'Shipments retrieved successfully',
        data: {
          content: [{ id: 5, trackingNumber: 'TRK-EXPRESS-9999', status: 'CREATED' }],
          totalElements: 1,
          totalPages: 1,
        },
      };

      const getSpy = vi.spyOn(api, 'get').mockResolvedValue(mockFiltered);

      const filters = {
        unassignedOnly: true,
        status: 'CREATED',
        search: 'EXPRESS',
        page: 0,
        size: 20,
      };

      const response = await adminService.getShipments(filters);

      expect(getSpy).toHaveBeenCalledWith('/admin/shipments', { params: filters });
      expect(response.data.content[0].trackingNumber).toBe('TRK-EXPRESS-9999');
    });

    it('getShipments() uses empty object default params when omitted', async () => {
      const getSpy = vi.spyOn(api, 'get').mockResolvedValue({ success: true, data: {} });

      await adminService.getShipments();

      expect(getSpy).toHaveBeenCalledWith('/admin/shipments', { params: {} });
    });

    it('getShipmentById() calls GET /admin/shipments/:id', async () => {
      const mockShipment = {
        success: true,
        message: 'Shipment details retrieved successfully',
        data: {
          id: 42,
          trackingNumber: 'TRK-20261031-ADMIN42',
          status: 'OUT_FOR_DELIVERY',
          senderName: 'Origin Depot',
          recipientName: 'Recipient Customer',
          price: 29.5,
          currency: 'USD',
        },
      };

      const getSpy = vi.spyOn(api, 'get').mockResolvedValue(mockShipment);

      const response = await adminService.getShipmentById(42);

      expect(getSpy).toHaveBeenCalledWith('/admin/shipments/42');
      expect(response.data.trackingNumber).toBe('TRK-20261031-ADMIN42');
    });

    it('getShipmentEvents() calls GET /admin/shipments/:id/events', async () => {
      const mockEvents = {
        success: true,
        message: 'Shipment tracking events retrieved successfully',
        data: [
          { status: 'CREATED', locationName: 'Main Hub', remarks: 'Registered' },
          { status: 'IN_TRANSIT', locationName: 'Highway Hub', remarks: 'In transit' },
        ],
      };

      const getSpy = vi.spyOn(api, 'get').mockResolvedValue(mockEvents);

      const response = await adminService.getShipmentEvents(42);

      expect(getSpy).toHaveBeenCalledWith('/admin/shipments/42/events');
      expect(response.data).toHaveLength(2);
      expect(response.data[0].status).toBe('CREATED');
    });

    it('getAgents() calls GET /admin/agents and handles list response without assuming pagination', async () => {
      const mockAgents = {
        success: true,
        message: 'Agent directory retrieved successfully',
        data: [
          {
            id: 10,
            fullName: 'Driver Alice',
            email: 'alice@driver.com',
            phoneNumber: '+1-555-0100',
            active: true,
            totalAssignedShipments: 5,
            activeDeliveriesCount: 2,
            deliveredShipmentsCount: 3,
          },
          {
            id: 11,
            fullName: 'Driver Bob',
            email: 'bob@driver.com',
            phoneNumber: '+1-555-0101',
            active: false,
            totalAssignedShipments: 0,
            activeDeliveriesCount: 0,
            deliveredShipmentsCount: 0,
          },
        ],
      };

      const getSpy = vi.spyOn(api, 'get').mockResolvedValue(mockAgents);

      const response = await adminService.getAgents();

      expect(getSpy).toHaveBeenCalledWith('/admin/agents', { params: {} });
      expect(Array.isArray(response.data)).toBe(true);
      expect(response.data).toHaveLength(2);
      expect(response.data[0].active).toBe(true);
      expect(response.data[1].active).toBe(false);
    });

    it('getFleet() calls GET /admin/fleet and returns active fleet member coordinates', async () => {
      const mockFleet = {
        success: true,
        message: 'Fleet telemetry locations retrieved successfully',
        data: [
          {
            agentId: 10,
            agentName: 'Driver Alice',
            shipmentId: 101,
            trackingNumber: 'TRK-20261031-FLEET01',
            status: 'OUT_FOR_DELIVERY',
            latitude: 30.2672,
            longitude: -97.7431,
            recordedAt: '2026-10-31T12:00:00Z',
          },
        ],
      };

      const getSpy = vi.spyOn(api, 'get').mockResolvedValue(mockFleet);

      const response = await adminService.getFleet();

      expect(getSpy).toHaveBeenCalledWith('/admin/fleet');
      expect(response.data[0].trackingNumber).toBe('TRK-20261031-FLEET01');
      expect(response.data[0].latitude).toBe(30.2672);
      expect(response.data[0].longitude).toBe(-97.7431);
    });
  });

  describe('JWT Interceptor & Authorization Header Preservation', () => {
    it('attaches Bearer token from localStorage to outgoing requests', async () => {
      localStorage.setItem('token', 'valid-admin-jwt-token-xyz');

      // Retrieve the request interceptor handler registered in api.interceptors.request
      const requestInterceptor = api.interceptors.request.handlers[0];
      expect(requestInterceptor).toBeDefined();

      const config = { headers: {} };
      const modifiedConfig = await requestInterceptor.fulfilled(config);

      expect(modifiedConfig.headers.Authorization).toBe('Bearer valid-admin-jwt-token-xyz');
    });

    it('does not attach Authorization header when no token is in localStorage', async () => {
      const requestInterceptor = api.interceptors.request.handlers[0];
      const config = { headers: {} };
      const modifiedConfig = await requestInterceptor.fulfilled(config);

      expect(modifiedConfig.headers.Authorization).toBeUndefined();
    });

    it('preserves existing Authorization header if already explicitly set', async () => {
      localStorage.setItem('token', 'local-token');

      const requestInterceptor = api.interceptors.request.handlers[0];
      const config = { headers: { Authorization: 'Bearer explicit-token' } };
      const modifiedConfig = await requestInterceptor.fulfilled(config);

      expect(modifiedConfig.headers.Authorization).toBe('Bearer explicit-token');
    });
  });

  describe('Error Handling and Propagation', () => {
    it('unwraps error.response.data on server rejection', async () => {
      const responseInterceptor = api.interceptors.response.handlers[0];
      expect(responseInterceptor).toBeDefined();

      const error = {
        response: {
          data: {
            success: false,
            message: 'Access denied: You do not have permission to perform this action',
          },
          status: 403,
        },
      };

      await expect(responseInterceptor.rejected(error)).rejects.toEqual({
        success: false,
        message: 'Access denied: You do not have permission to perform this action',
      });
    });

    it('falls back to standard message on network communication error', async () => {
      const responseInterceptor = api.interceptors.response.handlers[0];

      const networkError = {
        message: 'Network Error',
      };

      await expect(responseInterceptor.rejected(networkError)).rejects.toEqual({
        success: false,
        message: 'Network Error',
      });
    });

    it('unwraps response.data on successful server response', async () => {
      const responseInterceptor = api.interceptors.response.handlers[0];

      const rawResponse = {
        data: {
          success: true,
          data: { status: 'OK' },
        },
      };

      const result = responseInterceptor.fulfilled(rawResponse);
      expect(result).toEqual({ success: true, data: { status: 'OK' } });
    });
  });

  describe('Modular Re-export & Existing Services Integrity', () => {
    it('re-exports adminService identically from adminService.js', () => {
      expect(adminServiceDefault).toBe(adminService);
      expect(adminServiceNamed).toBe(adminService);
    });

    it('preserves existing services on api.js without regression', () => {
      expect(systemService).toBeDefined();
      expect(typeof systemService.getHealth).toBe('function');

      expect(authService).toBeDefined();
      expect(typeof authService.login).toBe('function');
      expect(typeof authService.registerCustomer).toBe('function');

      expect(trackingService).toBeDefined();
      expect(typeof trackingService.getPublicTracking).toBe('function');
      expect(typeof trackingService.getPublicLiveLocation).toBe('function');

      expect(shipmentService).toBeDefined();
      expect(typeof shipmentService.createShipment).toBe('function');
      expect(typeof shipmentService.getCustomerShipments).toBe('function');
      expect(typeof shipmentService.getPriceQuote).toBe('function');

      expect(agentService).toBeDefined();
      expect(typeof agentService.getMyAssignments).toBe('function');
      expect(typeof agentService.getMySummary).toBe('function');
    });
  });
});
