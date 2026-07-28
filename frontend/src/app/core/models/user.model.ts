export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  fullName: string;
  email: string;
  role: string;
  twoFactorRequired?: boolean;
  tempToken?: string;
}
